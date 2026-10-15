package com.email.bulk.repository;

import com.email.bulk.sending.ClaimedEmailJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        locations = "classpath:application-test.properties"
)
class EmailDeliveryRepositoryTest {

    @Autowired
    private EmailDeliveryRepository deliveryRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long campaignId;

    @BeforeEach
    void setUp() {

        assertEquals(
                "bulkemailtest",
                jdbcTemplate.queryForObject(
                        "SELECT DATABASE()", String.class
                )
        );

        // Global claim/recovery/completion queries must not touch
        // unrelated fixtures. Stop if this test database is not empty.
        assertEquals(
                0L,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM campaign", Long.class
                ).longValue(),
                "Test database contains campaigns; inspect before running"
        );

        campaignId = campaignRepository.insert(
                "Delivery repository test",
                "Test subject",
                "Test body"
        );

        jdbcTemplate.update(
                """
                INSERT INTO email_job (
                    campaign_id,
                    recipient_email,
                    status,
                    attempt_count,
                    next_attempt_at
                )
                VALUES (?, 'delivery-test@example.com', 'PENDING', 0,
                        TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6)))
                """,
                campaignId
        );
    }

    @AfterEach
    void cleanUp() {

        if (campaignId == null) {
            return;
        }

        jdbcTemplate.update(
                "DELETE FROM email_job WHERE campaign_id = ?",
                campaignId
        );

        jdbcTemplate.update(
                "DELETE FROM campaign WHERE id = ?",
                campaignId
        );
    }

    @Test
    void claimsOnlyAfterCampaignIsRunning() {

        assertTrue(deliveryRepository.claimNext(300, 3).isEmpty());

        makeRunning();

        ClaimedEmailJob work = claim();

        assertEquals(campaignId.longValue(), work.campaignId());
        assertEquals("delivery-test@example.com", work.recipientEmail());
        assertEquals("Test subject", work.subject());
        assertEquals("Test body", work.body());
        assertEquals(1, work.attemptCount());
        assertNotNull(work.workerToken());

        assertEquals("SENDING", jobStatus());

        assertEquals(
                work.workerToken(),
                jdbcTemplate.queryForObject(
                        """
                        SELECT worker_token FROM email_job
                        WHERE campaign_id = ?
                        """,
                        String.class,
                        campaignId
                )
        );

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM email_job
                        WHERE campaign_id = ?
                          AND attempt_count = 1
                          AND last_attempt_at IS NOT NULL
                          AND lease_expires_at > CURRENT_TIMESTAMP(6)
                        """,
                        Integer.class,
                        campaignId
                ).intValue()
        );

        // A SENDING job cannot be claimed again.
        assertTrue(deliveryRepository.claimNext(300, 3).isEmpty());
    }

    @Test
    void rejectsWrongTokenAndAcceptsCurrentOwner() {

        makeRunning();
        ClaimedEmailJob work = claim();

        ClaimedEmailJob wrongOwner = new ClaimedEmailJob(
                work.jobId(),
                work.campaignId(),
                work.recipientEmail(),
                work.subject(),
                work.body(),
                work.attemptCount(),
                UUID.randomUUID().toString()
        );

        assertFalse(deliveryRepository.markSent(wrongOwner));

        assertFalse(deliveryRepository.recordFailure(
                wrongOwner, "Wrong owner", 3, 3600
        ));

        assertEquals("SENDING", jobStatus());

        assertTrue(deliveryRepository.markSent(work));
        assertEquals("SENT", jobStatus());
        assertOwnershipCleared();

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM email_job
                        WHERE campaign_id = ?
                          AND sent_at IS NOT NULL
                          AND last_error IS NULL
                        """,
                        Integer.class,
                        campaignId
                ).intValue()
        );

        assertFalse(deliveryRepository.markSent(work));
    }

    @Test
    void retriesUntilThirdFailureThenStops() {

        makeRunning();

        for (int attempt = 1; attempt <= 3; attempt++) {

            ClaimedEmailJob work = claim();

            assertEquals(attempt, work.attemptCount());

            assertTrue(deliveryRepository.recordFailure(
                    work, "Simulated SMTP failure", 3, 3600
            ));

            assertEquals(
                    attempt == 3 ? "FAILED" : "RETRY",
                    jobStatus()
            );

            assertOwnershipCleared();

            // Future retry time must prevent immediate claim.
            assertTrue(deliveryRepository.claimNext(300, 3).isEmpty());

            // Move due time into the past without sleeping.
            makeJobDue();
        }

        // Even with a past due time, terminal FAILED stays unclaimable.
        assertTrue(deliveryRepository.claimNext(300, 3).isEmpty());
    }

    @Test
    void recoversExpiredLeaseAndRejectsOldOwner() {

        makeRunning();
        ClaimedEmailJob oldWork = claim();

        jdbcTemplate.update(
                """
                UPDATE email_job
                SET lease_expires_at =
                    TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6))
                WHERE campaign_id = ?
                """,
                campaignId
        );

        assertFalse(deliveryRepository.markSent(oldWork));
        assertFalse(deliveryRepository.recordFailure(
                oldWork, "Expired owner", 3, 3600
        ));

        assertEquals(
                1,
                deliveryRepository.recoverExpiredJobs(3, 3600)
        );

        assertEquals("RETRY", jobStatus());
        assertOwnershipCleared();

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT attempt_count FROM email_job
                        WHERE campaign_id = ?
                        """,
                        Integer.class,
                        campaignId
                ).intValue()
        );

        assertTrue(deliveryRepository.claimNext(300, 3).isEmpty());

        makeJobDue();
        ClaimedEmailJob newWork = claim();

        assertEquals(2, newWork.attemptCount());
        assertNotEquals(oldWork.workerToken(), newWork.workerToken());

        assertFalse(deliveryRepository.markSent(oldWork));
        assertFalse(deliveryRepository.recordFailure(
                oldWork, "Stale attempt", 3, 3600
        ));

        assertTrue(deliveryRepository.markSent(newWork));
        assertEquals("SENT", jobStatus());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completesOnlyAfterJobBecomesTerminal(boolean failJob) {

        makeRunning();

        // PENDING prevents completion.
        assertEquals(0, deliveryRepository.completeFinishedCampaigns());

        ClaimedEmailJob work = claim();

        // SENDING also prevents completion.
        assertEquals(0, deliveryRepository.completeFinishedCampaigns());

        if (failJob) {
            assertTrue(deliveryRepository.recordFailure(
                    work, "Terminal failure", 1, 3600
            ));
        } else {
            assertTrue(deliveryRepository.markSent(work));
        }

        assertEquals(1, deliveryRepository.completeFinishedCampaigns());

        assertEquals(
                failJob ? "COMPLETED_WITH_ERRORS" : "COMPLETED",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM campaign WHERE id = ?",
                        String.class,
                        campaignId
                )
        );

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM campaign
                        WHERE id = ? AND completed_at IS NOT NULL
                        """,
                        Integer.class,
                        campaignId
                ).intValue()
        );

        assertEquals(0, deliveryRepository.completeFinishedCampaigns());
    }

    private void makeRunning() {

        jdbcTemplate.update(
                """
                UPDATE campaign
                SET status = 'RUNNING',
                    started_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                """,
                campaignId
        );
    }

    private ClaimedEmailJob claim() {

        ClaimedEmailJob work = deliveryRepository.claimNext(300, 3)
                .orElseThrow(
                        () -> new AssertionError("Expected an eligible job")
                );

        assertEquals(campaignId.longValue(), work.campaignId());
        return work;
    }

    private void makeJobDue() {

        jdbcTemplate.update(
                """
                UPDATE email_job
                SET next_attempt_at =
                    TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6))
                WHERE campaign_id = ?
                """,
                campaignId
        );
    }

    private String jobStatus() {

        return jdbcTemplate.queryForObject(
                "SELECT status FROM email_job WHERE campaign_id = ?",
                String.class,
                campaignId
        );
    }

    private void assertOwnershipCleared() {

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM email_job
                        WHERE campaign_id = ?
                          AND worker_token IS NULL
                          AND lease_expires_at IS NULL
                        """,
                        Integer.class,
                        campaignId
                ).intValue()
        );
    }
}