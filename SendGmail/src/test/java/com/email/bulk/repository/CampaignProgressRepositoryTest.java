package com.email.bulk.repository;

import com.email.bulk.api.dto.CampaignProgressResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        locations = "classpath:application-test.properties"
)
@Transactional
class CampaignProgressRepositoryTest {

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void verifyTestDatabase() {

        assertEquals(
                "bulkemailtest",
                jdbcTemplate.queryForObject(
                        "SELECT DATABASE()",
                        String.class
                )
        );
    }

    @Test
    void returnsEmptyWhenCampaignDoesNotExist() {

        // Create and delete our own campaign to get a known missing ID.
        long campaignId = createCampaign();

        jdbcTemplate.update(
                "DELETE FROM campaign WHERE id = ?",
                campaignId
        );

        assertTrue(
                campaignRepository.findProgress(campaignId).isEmpty()
        );
    }

    @Test
    void returnsZeroProgressForCampaignWithoutRecipients() {

        long campaignId = createCampaign();

        CampaignProgressResponse result = progress(campaignId);

        assertAll(
                () -> assertEquals(campaignId, result.campaignId()),
                () -> assertEquals("DRAFT", result.status()),
                () -> assertEquals(0L, result.totalRecipients()),
                () -> assertEquals(0L, result.pending()),
                () -> assertEquals(0L, result.sending()),
                () -> assertEquals(0L, result.retry()),
                () -> assertEquals(0L, result.sent()),
                () -> assertEquals(0L, result.failed()),
                () -> assertEquals(0L, result.processedRecipients()),
                () -> assertEquals(
                        new BigDecimal("0.00"),
                        result.progressPercent()
                )
        );
    }

    @Test
    void countsEachStatusAndIncludesFailuresInProcessedTotal() {

        long campaignId = createCampaign();

        addJob(campaignId, "pending1@example.com", "PENDING");
        addJob(campaignId, "pending2@example.com", "PENDING");
        addJob(campaignId, "sending@example.com", "SENDING");
        addJob(campaignId, "retry@example.com", "RETRY");
        addJob(campaignId, "sent1@example.com", "SENT");
        addJob(campaignId, "sent2@example.com", "SENT");
        addJob(campaignId, "sent3@example.com", "SENT");
        addJob(campaignId, "failed@example.com", "FAILED");

        CampaignProgressResponse result = progress(campaignId);

        assertAll(
                () -> assertEquals(8L, result.totalRecipients()),
                () -> assertEquals(2L, result.pending()),
                () -> assertEquals(1L, result.sending()),
                () -> assertEquals(1L, result.retry()),
                () -> assertEquals(3L, result.sent()),
                () -> assertEquals(1L, result.failed()),
                () -> assertEquals(4L, result.processedRecipients()),
                () -> assertEquals(
                        new BigDecimal("50.00"),
                        result.progressPercent()
                )
        );
    }

    @Test
    void roundsOneOfThreeProcessedTo33Point33() {

        long campaignId = createCampaign();

        addJob(campaignId, "sent@example.com", "SENT");
        addJob(campaignId, "pending@example.com", "PENDING");
        addJob(campaignId, "retry@example.com", "RETRY");

        CampaignProgressResponse result = progress(campaignId);

        assertEquals(3L, result.totalRecipients());
        assertEquals(1L, result.processedRecipients());
        assertEquals(
                new BigDecimal("33.33"),
                result.progressPercent()
        );
    }

    @Test
    void roundsTwoOfThreeProcessedTo66Point67() {

        long campaignId = createCampaign();

        addJob(campaignId, "sent@example.com", "SENT");
        addJob(campaignId, "failed@example.com", "FAILED");
        addJob(campaignId, "pending@example.com", "PENDING");

        CampaignProgressResponse result = progress(campaignId);

        assertEquals(2L, result.processedRecipients());
        assertEquals(
                new BigDecimal("66.67"),
                result.progressPercent()
        );
    }

    @Test
    void returns100PercentWhenAllJobsAreTerminal() {

        long campaignId = createCampaign();

        addJob(campaignId, "sent@example.com", "SENT");
        addJob(campaignId, "failed@example.com", "FAILED");

        jdbcTemplate.update(
                """
                UPDATE campaign
                SET status = 'COMPLETED_WITH_ERRORS'
                WHERE id = ?
                """,
                campaignId
        );

        CampaignProgressResponse result = progress(campaignId);

        assertAll(
                () -> assertEquals(
                        "COMPLETED_WITH_ERRORS",
                        result.status()
                ),
                () -> assertEquals(2L, result.totalRecipients()),
                () -> assertEquals(2L, result.processedRecipients()),
                () -> assertEquals(1L, result.sent()),
                () -> assertEquals(1L, result.failed()),
                () -> assertEquals(
                        new BigDecimal("100.00"),
                        result.progressPercent()
                )
        );
    }

    @Test
    void excludesJobsBelongingToAnotherCampaign() {

        long firstCampaignId = createCampaign();
        long secondCampaignId = createCampaign();

        // Same address is valid across different campaigns.
        addJob(firstCampaignId, "same@example.com", "PENDING");
        addJob(secondCampaignId, "same@example.com", "SENT");
        addJob(secondCampaignId, "other@example.com", "FAILED");

        CampaignProgressResponse result = progress(firstCampaignId);

        assertAll(
                () -> assertEquals(
                        firstCampaignId,
                        result.campaignId()
                ),
                () -> assertEquals(1L, result.totalRecipients()),
                () -> assertEquals(1L, result.pending()),
                () -> assertEquals(0L, result.sent()),
                () -> assertEquals(0L, result.failed()),
                () -> assertEquals(0L, result.processedRecipients()),
                () -> assertEquals(
                        new BigDecimal("0.00"),
                        result.progressPercent()
                )
        );
    }

    private long createCampaign() {

        return campaignRepository.insert(
                "Progress integration test",
                "Test subject",
                "Test body"
        );
    }

    private void addJob(
            long campaignId,
            String recipientEmail,
            String status) {

        jdbcTemplate.update(
                """
                INSERT INTO email_job (
                    campaign_id,
                    recipient_email,
                    status,
                    attempt_count
                )
                VALUES (?, ?, ?, 0)
                """,
                campaignId,
                recipientEmail,
                status
        );
    }

    private CampaignProgressResponse progress(long campaignId) {

        return campaignRepository.findProgress(campaignId)
                .orElseThrow(
                        () -> new AssertionError(
                                "Test campaign was not found"
                        )
                );
    }
}