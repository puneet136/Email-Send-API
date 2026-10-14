package com.email.bulk.importing;

import com.email.bulk.exception.ImportLeaseLostException;
import com.email.bulk.repository.RecipientImportRepository;
import com.email.bulk.service.LeaseAwareRecipientImportBatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(
        prefix = "app.import.worker",
        name = "enabled",
        havingValue = "true"
)
public class RecipientImportWorker {

    private static final Logger log =
            LoggerFactory.getLogger(RecipientImportWorker.class);

    private final RecipientImportRepository importRepository;
    private final RecipientCsvParser csvParser;
    private final LeaseAwareRecipientImportBatchService batchService;
    private final int leaseSeconds;

    public RecipientImportWorker(
            RecipientImportRepository importRepository,
            RecipientCsvParser csvParser,
            LeaseAwareRecipientImportBatchService batchService,
            @Value("${app.import.worker.lease-seconds:300}")
            int leaseSeconds) {

        if (leaseSeconds < 30 || leaseSeconds > 3600) {
            throw new IllegalArgumentException(
                    "Lease duration must be between 30 and 3600 seconds"
            );
        }

        this.importRepository = importRepository;
        this.csvParser = csvParser;
        this.batchService = batchService;
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.import.worker.poll-delay-ms:5000}",
            initialDelayString =
                    "${app.import.worker.poll-delay-ms:5000}"
    )
    public void poll() {

        try {
            importRepository
                    .claimNextAvailableWithLease(leaseSeconds)
                    .ifPresent(this::process);

        } catch (RuntimeException exception) {
            log.error("Import worker polling failed", exception);
        }
    }

    private void process(ClaimedRecipientImport work) {

        AtomicLong processedRows =
                new AtomicLong(work.processedRows());

        AtomicLong rowsToSkip =
                new AtomicLong(work.processedRows());

        try {
            if (work.processedRows() < 0) {
                throw new IllegalStateException(
                        "Stored processed-row count must not be negative"
                );
            }

            csvParser.readBatches(
                    work.storedFilename(),
                    rows -> {
                        int skipCount = (int) Math.min(
                                rowsToSkip.get(),
                                (long) rows.size()
                        );

                        if (skipCount > 0) {
                            rowsToSkip.addAndGet(-skipCount);

                            batchService.heartbeat(
                                    work,
                                    processedRows.get(),
                                    leaseSeconds
                            );
                        }

                        // This entire batch was committed earlier.
                        if (skipCount == rows.size()) {
                            return;
                        }

                        List<CsvRecipientRow> pendingRows =
                                List.copyOf(
                                        rows.subList(
                                                skipCount,
                                                rows.size()
                                        )
                                );

                        batchService.processBatch(
                                work,
                                processedRows.get(),
                                pendingRows,
                                leaseSeconds
                        );

                        // Advance only after the transaction commits.
                        processedRows.addAndGet(pendingRows.size());
                    }
            );

            if (rowsToSkip.get() != 0) {
                throw new IllegalStateException(
                        "CSV has fewer data records than "
                                + "the stored processed-row count"
                );
            }

            importRepository.markCompletedWithLease(
                    work.importId(),
                    work.campaignId(),
                    work.workerToken(),
                    processedRows.get()
            );

            log.info(
                    "Import {} completed with {} processed rows; "
                            + "initial checkpoint was {}",
                    work.importId(),
                    processedRows.get(),
                    work.processedRows()
            );

        } catch (ImportLeaseLostException exception) {
            log.warn(
                    "Import {} stopped because worker ownership "
                            + "was lost or the lease expired",
                    work.importId()
            );

        } catch (IOException | RuntimeException exception) {
            log.error(
                    "Import {} processing failed",
                    work.importId(),
                    exception
            );

            recordFailure(work);
        }
    }

    private void recordFailure(ClaimedRecipientImport work) {

        try {
            boolean updated =
                    importRepository.markFailedWithLease(
                            work.importId(),
                            work.campaignId(),
                            work.workerToken(),
                            "Import processing failed. "
                                    + "Check application logs."
                    );

            if (!updated) {
                log.warn(
                        "Failure status was not written for import {}: "
                                + "ownership, lease or status changed",
                        work.importId()
                );
            }

        } catch (RuntimeException exception) {
            log.error(
                    "Could not record failure for import {}",
                    work.importId(),
                    exception
            );
        }
    }
}