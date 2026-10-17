package com.email.bulk.repository;

import com.email.bulk.exception.ImportLeaseLostException;
import com.email.bulk.importing.ClaimedRecipientImport;
import com.email.bulk.importing.CsvRecipientRow;
import com.email.bulk.service.LeaseAwareRecipientImportBatchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        locations = "classpath:application-test.properties"
)
class RecipientImportLeaseRepositoryTest {

    private static final int LEASE_SECONDS = 3600;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private RecipientImportRepository importRepository;

    @Autowired
    private LeaseAwareRecipientImportBatchService batchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long campaignId;
    private Long importId;

    @BeforeEach
    void setUp() {
        assertEquals(
                "bulkemailtest",
                jdbcTemplate.queryForObject(
                        "SELECT DATABASE()",
                        String.class
                )
        );

        // Claim queries can select any eligible import.
        // Stop before creating fixtures if the test DB is not isolated.
        assertEquals(
                0L,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM recipient_import",
                        Long.class
                ).longValue(),
                "Test database must contain no existing imports"
        );

        campaignId = campaignRepository.insert(
                "Import lease test",
                "Test subject",
                "Test body"
        );

        importId = importRepository.insert(
                campaignId,
                "lease-test.csv",
                "lease-test-" + UUID.randomUUID() + ".csv",
                100L
        );
    }

    @AfterEach
    void cleanUp() {
        if (importId != null) {
            jdbcTemplate.update(
                    "DELETE FROM recipient_import_error WHERE import_id = ?",
                    importId
            );

            jdbcTemplate.update(
                    "DELETE FROM recipient_import WHERE id = ?",
                    importId
            );
        }

        if (campaignId != null) {
            jdbcTemplate.update(
                    "DELETE FROM email_job WHERE campaign_id = ?",
                    campaignId
            );

            jdbcTemplate.update(
                    "DELETE FROM campaign WHERE id = ?",
                    campaignId
            );
        }
    }

    @Test
    void reclaimPreservesCheckpointAndRejectsOldWorkerUpdates() {
        ClaimedRecipientImport oldWork = claim();

        batchService.processBatch(
                oldWork,
                0L,
                List.of(row(2, "first@example.com")),
                LEASE_SECONDS
        );

        Object originalStartedAt = snapshot().get("started_at");

        expireLease();

        ClaimedRecipientImport newWork = claim();

        assertEquals(1L, newWork.processedRows());
        assertNotEquals(
                oldWork.workerToken(),
                newWork.workerToken()
        );
        assertEquals(
                oldWork.storedFilename(),
                newWork.storedFilename()
        );

        Map<String, Object> before = snapshot();

        assertEquals("RUNNING", before.get("status"));
        assertEquals(
                2L,
                ((Number) before.get("claim_count")).longValue()
        );
        assertEquals(originalStartedAt, before.get("started_at"));
        assertEquals(newWork.workerToken(), before.get("worker_token"));

        // Use the correct checkpoint, so rejection tests old ownership.
        assertThrows(
                ImportLeaseLostException.class,
                () -> batchService.processBatch(
                        oldWork,
                        1L,
                        List.of(row(3, "stale@example.com")),
                        LEASE_SECONDS
                )
        );

        assertThrows(
                ImportLeaseLostException.class,
                () -> batchService.heartbeat(
                        oldWork,
                        1L,
                        LEASE_SECONDS
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> complete(oldWork, 1L)
        );

        assertFalse(fail(oldWork));

        // Rejected operations must leave the new owner's state intact.
        assertEquals(before, snapshot());
        assertEquals(1L, recipientCount());

        assertEquals(
                0L,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM email_job
                        WHERE campaign_id = ?
                          AND recipient_email = ?
                        """,
                        Long.class,
                        campaignId,
                        "stale@example.com"
                ).longValue()
        );

        // The new owner can continue from the saved checkpoint.
        batchService.processBatch(
                newWork,
                1L,
                List.of(row(3, "second@example.com")),
                LEASE_SECONDS
        );

        complete(newWork, 2L);

        assertEquals(2L, recipientCount());
        assertCompleted(2L);
    }

    @Test
    void expiredLeaseCannotWriteEvenBeforeAnotherWorkerClaimsIt() {
        ClaimedRecipientImport work = claim();

        expireLease();

        Map<String, Object> before = snapshot();

        assertThrows(
                ImportLeaseLostException.class,
                () -> batchService.heartbeat(
                        work,
                        0L,
                        LEASE_SECONDS
                )
        );

        assertThrows(
                ImportLeaseLostException.class,
                () -> batchService.processBatch(
                        work,
                        0L,
                        List.of(row(2, "expired@example.com")),
                        LEASE_SECONDS
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> complete(work, 0L)
        );

        assertFalse(fail(work));

        assertEquals(before, snapshot());
        assertEquals(0L, recipientCount());
    }

    @Test
    void activeLeaseCannotBeReclaimedAndWrongCheckpointIsRejected() {
        ClaimedRecipientImport work = claim();

        batchService.processBatch(
                work,
                0L,
                List.of(row(2, "valid@example.com")),
                LEASE_SECONDS
        );

        Map<String, Object> before = snapshot();

        assertTrue(
                importRepository
                        .claimNextAvailableWithLease(LEASE_SECONDS)
                        .isEmpty()
        );

        assertThrows(
                ImportLeaseLostException.class,
                () -> batchService.heartbeat(
                        work,
                        0L,
                        LEASE_SECONDS
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> complete(work, 0L)
        );

        assertEquals(before, snapshot());

        // Correct owner and checkpoint can still finish the import.
        complete(work, 1L);

        assertCompleted(1L);
    }

    private ClaimedRecipientImport claim() {
        ClaimedRecipientImport work = importRepository
                .claimNextAvailableWithLease(LEASE_SECONDS)
                .orElseThrow();

        assertEquals(importId.longValue(), work.importId());
        assertEquals(campaignId.longValue(), work.campaignId());
        assertNotNull(work.workerToken());
        assertFalse(work.workerToken().isBlank());

        return work;
    }

    private void expireLease() {
        int updated = jdbcTemplate.update(
                """
                UPDATE recipient_import
                SET lease_expires_at = TIMESTAMPADD(
                    SECOND, -60, CURRENT_TIMESTAMP(6)
                )
                WHERE id = ?
                """,
                importId
        );

        assertEquals(1, updated);
    }

    private void complete(
            ClaimedRecipientImport work,
            long expectedProcessedRows) {

        importRepository.markCompletedWithLease(
                work.importId(),
                work.campaignId(),
                work.workerToken(),
                expectedProcessedRows
        );
    }

    private boolean fail(ClaimedRecipientImport work) {
        return importRepository.markFailedWithLease(
                work.importId(),
                work.campaignId(),
                work.workerToken(),
                "Failure from rejected worker"
        );
    }

    private Map<String, Object> snapshot() {
        return jdbcTemplate.queryForMap(
                """
                SELECT status,
                       processed_rows,
                       added_rows,
                       duplicate_rows,
                       invalid_rows,
                       claim_count,
                       worker_token,
                       lease_expires_at,
                       heartbeat_at,
                       started_at,
                       completed_at,
                       last_error
                FROM recipient_import
                WHERE id = ?
                """,
                importId
        );
    }

    private long recipientCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_job WHERE campaign_id = ?",
                Long.class,
                campaignId
        );
    }

    private void assertCompleted(long expectedRows) {
        Map<String, Object> state = snapshot();

        assertEquals("COMPLETED", state.get("status"));
        assertEquals(
                expectedRows,
                ((Number) state.get("processed_rows")).longValue()
        );
        assertEquals(
                expectedRows,
                ((Number) state.get("added_rows")).longValue()
        );
        assertEquals(
                0L,
                ((Number) state.get("duplicate_rows")).longValue()
        );
        assertEquals(
                0L,
                ((Number) state.get("invalid_rows")).longValue()
        );

        assertNotNull(state.get("completed_at"));
        assertNull(state.get("worker_token"));
        assertNull(state.get("lease_expires_at"));
        assertNull(state.get("last_error"));
    }

    private CsvRecipientRow row(long number, String email) {
        return new CsvRecipientRow(number, email, null);
    }
}