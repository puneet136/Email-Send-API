package com.email.bulk.service;

import com.email.bulk.importing.ClaimedRecipientImport;
import com.email.bulk.importing.CsvRecipientRow;
import com.email.bulk.repository.CampaignRepository;
import com.email.bulk.repository.RecipientImportRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        locations = "classpath:application-test.properties"
)
class RecipientImportBatchTransactionTest {

    @Autowired
    private CampaignRepository campaignRepository;

    @MockitoSpyBean
    private RecipientImportRepository importRepository;

    @Autowired
    private LeaseAwareRecipientImportBatchService batchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long campaignId;
    private Long importId;
    private ClaimedRecipientImport work;

    @BeforeEach
    void setUp() {

        assertEquals(
                "bulkemailtest",
                jdbcTemplate.queryForObject(
                        "SELECT DATABASE()", String.class
                )
        );

        // Claim query scans available imports globally.
        assertEquals(
                0L,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM recipient_import",
                        Long.class
                ).longValue(),
                "Test database contains imports; inspect before running"
        );

        campaignId = campaignRepository.insert(
                "Import transaction test",
                "Test subject",
                "Test body"
        );

        importId = importRepository.insert(
                campaignId,
                "transaction-test.csv",
                UUID.randomUUID() + ".csv",
                100L
        );

        work = importRepository.claimNextAvailableWithLease(300)
                .orElseThrow(
                        () -> new AssertionError(
                                "Expected the test import to be claimed"
                        )
                );

        assertEquals(importId.longValue(), work.importId());
        assertEquals(campaignId.longValue(), work.campaignId());
    }

    @AfterEach
    void cleanUp() {

        if (importId != null) {
            jdbcTemplate.update(
                    """
                    DELETE FROM recipient_import_error
                    WHERE import_id = ?
                    """,
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
    void commitsRecipientsErrorsAndProgressTogether() {

        commitFirstBatch();

        assertSnapshot(3, 1, 1, 1, 1, 1);

        assertEquals(
                "puneet@example.com",
                jdbcTemplate.queryForObject(
                        """
                        SELECT recipient_email
                        FROM email_job
                        WHERE campaign_id = ?
                        """,
                        String.class,
                        campaignId
                )
        );

        assertEquals(
                4L,
                jdbcTemplate.queryForObject(
                        """
                        SELECT record_number
                        FROM recipient_import_error
                        WHERE import_id = ?
                        """,
                        Long.class,
                        importId
                ).longValue()
        );

        assertEquals(
                "RUNNING",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM recipient_import WHERE id = ?",
                        String.class,
                        importId
                )
        );
    }

    @Test
    void rollsBackFailedBatchButPreservesEarlierCommittedBatch() {

        commitFirstBatch();
        assertSnapshot(3, 1, 1, 1, 1, 1);

        // Stub the underlying spy, avoiding a MANDATORY transaction
        // check while configuring Mockito outside a transaction.
        RecipientImportRepository target =
                AopTestUtils.getUltimateTargetObject(importRepository);

        doAnswer(invocation -> {

            // This executes inside the actual batch transaction.
            // Verify all new writes happened before injecting failure.
            assertSnapshot(5, 2, 1, 2, 2, 2);

            throw new IllegalStateException(
                    "Injected failure before lease renewal"
            );

        }).when(target).renewActiveLease(
                work.importId(),
                work.campaignId(),
                work.workerToken(),
                300
        );

        List<CsvRecipientRow> secondBatch = List.of(
                new CsvRecipientRow(
                        5L, "rahul@example.com", null
                ),
                new CsvRecipientRow(
                        6L, "invalid-address", null
                )
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> batchService.processBatch(
                        work,
                        3L,
                        secondBatch,
                        300
                )
        );

        assertEquals(
                "Injected failure before lease renewal",
                exception.getMessage()
        );

        // Service transaction has ended and rolled back.
        // Only the earlier committed batch must remain.
        assertSnapshot(3, 1, 1, 1, 1, 1);

        assertEquals(
                0L,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM email_job
                        WHERE campaign_id = ?
                          AND recipient_email = 'rahul@example.com'
                        """,
                        Long.class,
                        campaignId
                ).longValue()
        );

        assertEquals(
                0L,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM recipient_import_error
                        WHERE import_id = ?
                          AND record_number = 6
                        """,
                        Long.class,
                        importId
                ).longValue()
        );
    }

    private void commitFirstBatch() {

        batchService.processBatch(
                work,
                0L,
                List.of(
                        new CsvRecipientRow(
                                2L, "puneet@EXAMPLE.COM", null
                        ),
                        new CsvRecipientRow(
                                3L, "puneet@example.com", null
                        ),
                        new CsvRecipientRow(
                                4L, "not-an-email", null
                        )
                ),
                300
        );
    }

    private void assertSnapshot(
            long processed,
            long added,
            long duplicates,
            long invalid,
            long jobs,
            long errors) {

        long[] counters = jdbcTemplate.queryForObject(
                """
                SELECT processed_rows, added_rows,
                       duplicate_rows, invalid_rows
                FROM recipient_import
                WHERE id = ?
                """,
                (rs, rowNumber) -> new long[]{
                        rs.getLong("processed_rows"),
                        rs.getLong("added_rows"),
                        rs.getLong("duplicate_rows"),
                        rs.getLong("invalid_rows")
                },
                importId
        );

        assertArrayEquals(
                new long[]{processed, added, duplicates, invalid},
                counters
        );

        assertEquals(
                jobs,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM email_job
                        WHERE campaign_id = ?
                        """,
                        Long.class,
                        campaignId
                ).longValue()
        );

        assertEquals(
                errors,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM recipient_import_error
                        WHERE import_id = ?
                        """,
                        Long.class,
                        importId
                ).longValue()
        );
    }
}