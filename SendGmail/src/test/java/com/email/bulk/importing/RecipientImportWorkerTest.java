package com.email.bulk.importing;

import com.email.bulk.exception.ImportLeaseLostException;
import com.email.bulk.repository.RecipientImportRepository;
import com.email.bulk.service.LeaseAwareRecipientImportBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecipientImportWorkerTest {

    private static final int LEASE_SECONDS = 300;

    private RecipientImportRepository importRepository;
    private RecipientCsvParser csvParser;
    private LeaseAwareRecipientImportBatchService batchService;
    private RecipientImportWorker worker;

    @BeforeEach
    void setUp() {
        importRepository = mock(RecipientImportRepository.class);
        csvParser = mock(RecipientCsvParser.class);
        batchService = mock(LeaseAwareRecipientImportBatchService.class);

        worker = new RecipientImportWorker(
                importRepository,
                csvParser,
                batchService,
                LEASE_SECONDS
        );
    }

    @Test
    void doesNothingWhenNoImportIsAvailable() {
        when(importRepository.claimNextAvailableWithLease(LEASE_SECONDS))
                .thenReturn(Optional.empty());

        worker.poll();

        verify(importRepository)
                .claimNextAvailableWithLease(LEASE_SECONDS);

        verifyNoMoreInteractions(importRepository);
        verifyNoInteractions(csvParser, batchService);
    }

    @Test
    void processesBatchesAndAdvancesCheckpointBeforeCompletion()
            throws IOException {

        ClaimedRecipientImport work = claim(0);

        List<CsvRecipientRow> firstBatch = List.of(row(2), row(3));
        List<CsvRecipientRow> secondBatch = List.of(row(4));

        emitBatches(List.of(firstBatch, secondBatch));

        worker.poll();

        InOrder order = inOrder(batchService, importRepository);

        order.verify(batchService).processBatch(
                work, 0L, firstBatch, LEASE_SECONDS
        );

        order.verify(batchService).processBatch(
                work, 2L, secondBatch, LEASE_SECONDS
        );

        order.verify(importRepository).markCompletedWithLease(
                work.importId(),
                work.campaignId(),
                work.workerToken(),
                3L
        );

        verify(batchService, never())
                .heartbeat(any(), anyLong(), anyInt());

        verifyNoFailureRecorded();
    }

    @Test
    void skipsFullyCommittedBatchAndProcessesRemainingRows()
            throws IOException {

        ClaimedRecipientImport work = claim(2);

        List<CsvRecipientRow> committedBatch =
                List.of(row(2), row(3));

        List<CsvRecipientRow> pendingBatch =
                List.of(row(4), row(5));

        emitBatches(List.of(committedBatch, pendingBatch));

        worker.poll();

        InOrder order = inOrder(batchService, importRepository);

        order.verify(batchService).heartbeat(
                work, 2L, LEASE_SECONDS
        );

        order.verify(batchService).processBatch(
                work, 2L, pendingBatch, LEASE_SECONDS
        );

        order.verify(importRepository).markCompletedWithLease(
                work.importId(),
                work.campaignId(),
                work.workerToken(),
                4L
        );

        verify(batchService, times(1)).processBatch(
                any(), anyLong(), anyList(), anyInt()
        );

        verifyNoFailureRecorded();
    }

    @Test
    void skipsOnlyCommittedPrefixWithinBatch()
            throws IOException {

        ClaimedRecipientImport work = claim(1);

        List<CsvRecipientRow> batch =
                List.of(row(2), row(3), row(4));

        emitBatches(List.of(batch));

        worker.poll();

        InOrder order = inOrder(batchService, importRepository);

        order.verify(batchService).heartbeat(
                work, 1L, LEASE_SECONDS
        );

        order.verify(batchService).processBatch(
                work,
                1L,
                List.of(row(3), row(4)),
                LEASE_SECONDS
        );

        order.verify(importRepository).markCompletedWithLease(
                work.importId(),
                work.campaignId(),
                work.workerToken(),
                3L
        );

        verify(batchService, times(1)).processBatch(
                any(), anyLong(), anyList(), anyInt()
        );

        verifyNoFailureRecorded();
    }

    @Test
    void marksFailedWhenFileHasFewerRowsThanCheckpoint()
            throws IOException {

        ClaimedRecipientImport work = claim(3);

        emitBatches(List.of(
                List.of(row(2), row(3))
        ));

        worker.poll();

        verify(batchService).heartbeat(
                work, 3L, LEASE_SECONDS
        );

        verify(batchService, never()).processBatch(
                any(), anyLong(), anyList(), anyInt()
        );

        verifyFailureRecorded(work);
        verifyNoCompletionRecorded();
    }

    @Test
    void stopsWithoutWritingFailureWhenHeartbeatLosesLease()
            throws IOException {

        ClaimedRecipientImport work = claim(2);

        emitBatches(List.of(
                List.of(row(2), row(3)),
                List.of(row(4))
        ));

        ImportLeaseLostException leaseLost =
                mock(ImportLeaseLostException.class);

        doThrow(leaseLost)
                .when(batchService)
                .heartbeat(work, 2L, LEASE_SECONDS);

        assertDoesNotThrow(worker::poll);

        verify(batchService).heartbeat(
                work, 2L, LEASE_SECONDS
        );

        verify(batchService, never()).processBatch(
                any(), anyLong(), anyList(), anyInt()
        );

        verifyNoFailureRecorded();
        verifyNoCompletionRecorded();
    }

    @Test
    void stopsAfterBatchFailureWithoutProcessingLaterBatches()
            throws IOException {

        ClaimedRecipientImport work = claim(0);

        List<CsvRecipientRow> firstBatch =
                List.of(row(2), row(3));

        emitBatches(List.of(
                firstBatch,
                List.of(row(4))
        ));

        doThrow(new IllegalStateException("Simulated batch failure"))
                .when(batchService)
                .processBatch(
                        work, 0L, firstBatch, LEASE_SECONDS
                );

        assertDoesNotThrow(worker::poll);

        verify(batchService).processBatch(
                work, 0L, firstBatch, LEASE_SECONDS
        );

        verify(batchService, times(1)).processBatch(
                any(), anyLong(), anyList(), anyInt()
        );

        verifyFailureRecorded(work);
        verifyNoCompletionRecorded();
    }

    @Test
    void recordsFailureWhenCsvCannotBeRead()
            throws IOException {

        ClaimedRecipientImport work = claim(0);

        doThrow(new IOException("Simulated CSV read failure"))
                .when(csvParser)
                .readBatches(eq(work.storedFilename()), any());

        assertDoesNotThrow(worker::poll);

        verifyNoInteractions(batchService);
        verifyFailureRecorded(work);
        verifyNoCompletionRecorded();
    }

    private ClaimedRecipientImport claim(long processedRows) {
        ClaimedRecipientImport work = new ClaimedRecipientImport(
                101L,
                10L,
                "recipients-test.csv",
                processedRows,
                "test-worker-token"
        );

        when(importRepository.claimNextAvailableWithLease(LEASE_SECONDS))
                .thenReturn(Optional.of(work));

        return work;
    }

    private void emitBatches(List<List<CsvRecipientRow>> batches)
            throws IOException {

        doAnswer(invocation -> {
            Consumer<List<CsvRecipientRow>> consumer =
                    invocation.getArgument(1);

            for (List<CsvRecipientRow> batch : batches) {
                consumer.accept(batch);
            }

            return null;
        }).when(csvParser).readBatches(
                eq("recipients-test.csv"),
                any()
        );
    }

    private CsvRecipientRow row(long recordNumber) {
        return new CsvRecipientRow(
                recordNumber,
                "user" + recordNumber + "@example.com",
                null
        );
    }

    private void verifyFailureRecorded(ClaimedRecipientImport work) {
        verify(importRepository).markFailedWithLease(
                work.importId(),
                work.campaignId(),
                work.workerToken(),
                "Import processing failed. Check application logs."
        );
    }

    private void verifyNoFailureRecorded() {
        verify(importRepository, never()).markFailedWithLease(
                anyLong(),
                anyLong(),
                anyString(),
                anyString()
        );
    }

    private void verifyNoCompletionRecorded() {
        verify(importRepository, never()).markCompletedWithLease(
                anyLong(),
                anyLong(),
                anyString(),
                anyLong()
        );
    }
}