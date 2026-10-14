package com.email.bulk.service;

import com.email.bulk.importing.ClaimedRecipientImport;
import com.email.bulk.importing.CsvRecipientRow;
import com.email.bulk.repository.RecipientImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LeaseAwareRecipientImportBatchService {

    private final RecipientImportRepository importRepository;
    private final RecipientImportBatchService batchService;

    public LeaseAwareRecipientImportBatchService(
            RecipientImportRepository importRepository,
            RecipientImportBatchService batchService) {

        this.importRepository = importRepository;
        this.batchService = batchService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processBatch(
            ClaimedRecipientImport work,
            long expectedProcessedRows,
            List<CsvRecipientRow> rows,
            int leaseSeconds) {

        if (work == null) {
            throw new IllegalArgumentException(
                    "Claimed import must not be null"
            );
        }

        if (expectedProcessedRows < 0) {
            throw new IllegalArgumentException(
                    "Expected processed rows must not be negative"
            );
        }

        if (rows == null || rows.isEmpty() || rows.size() > 500) {
            throw new IllegalArgumentException(
                    "Batch must contain between 1 and 500 rows"
            );
        }

        if (leaseSeconds < 30 || leaseSeconds > 3600) {
            throw new IllegalArgumentException(
                    "Lease duration must be between 30 and 3600 seconds"
            );
        }

        importRepository.lockActiveLease(
                work.importId(),
                work.campaignId(),
                work.workerToken(),
                expectedProcessedRows
        );

        batchService.processBatch(
                work.importId(),
                work.campaignId(),
                expectedProcessedRows,
                rows
        );

        importRepository.renewActiveLease(
                work.importId(),
                work.campaignId(),
                work.workerToken(),
                leaseSeconds
        );
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void heartbeat(
            ClaimedRecipientImport work,
            long expectedProcessedRows,
            int leaseSeconds) {

        if (work == null) {
            throw new IllegalArgumentException(
                    "Claimed import must not be null"
            );
        }

        if (leaseSeconds < 30 || leaseSeconds > 3600) {
            throw new IllegalArgumentException(
                    "Lease duration must be between 30 and 3600 seconds"
            );
        }

        importRepository.lockActiveLease(
                work.importId(),
                work.campaignId(),
                work.workerToken(),
                expectedProcessedRows
        );

        importRepository.renewActiveLease(
                work.importId(),
                work.campaignId(),
                work.workerToken(),
                leaseSeconds
        );
    }

}