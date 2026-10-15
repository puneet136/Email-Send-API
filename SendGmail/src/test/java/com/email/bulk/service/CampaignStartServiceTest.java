package com.email.bulk.service;

import com.email.bulk.api.dto.CampaignStartResponse;
import com.email.bulk.exception.CampaignConflictException;
import com.email.bulk.exception.CampaignNotFoundException;
import com.email.bulk.repository.CampaignRepository;
import com.email.bulk.repository.RecipientImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        locations = "classpath:application-test.properties"
)
@Transactional
class CampaignStartServiceTest {

    @Autowired
    private CampaignStartService campaignStartService;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private RecipientImportRepository importRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void verifyTestConfiguration() {

        assertEquals(
                "bulkemailtest",
                jdbcTemplate.queryForObject(
                        "SELECT DATABASE()",
                        String.class
                )
        );
    }

    @Test
    void rejectsMissingCampaign() {

        long campaignId = createCampaign();

        jdbcTemplate.update(
                "DELETE FROM campaign WHERE id = ?",
                campaignId
        );

        assertThrows(
                CampaignNotFoundException.class,
                () -> campaignStartService.start(campaignId)
        );
    }

    @Test
    void rejectsCampaignWithoutPendingRecipients() {

        long campaignId = createCampaign();

        assertThrows(
                CampaignConflictException.class,
                () -> campaignStartService.start(campaignId)
        );

        assertStillDraft(campaignId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUEUED", "RUNNING"})
    void rejectsCampaignWithActiveImport(String importStatus) {

        long campaignId = createCampaign();
        addPendingRecipient(campaignId);

        long importId = importRepository.insert(
                campaignId,
                "start-test.csv",
                UUID.randomUUID() + ".csv",
                100L
        );

        jdbcTemplate.update(
                """
                UPDATE recipient_import
                SET status = ?
                WHERE id = ?
                """,
                importStatus,
                importId
        );

        assertThrows(
                CampaignConflictException.class,
                () -> campaignStartService.start(campaignId)
        );

        assertStillDraft(campaignId);
    }

    @Test
    void startsDraftCampaignWithPendingRecipient() {

        long campaignId = createCampaign();
        addPendingRecipient(campaignId);

        CampaignStartResponse response =
                campaignStartService.start(campaignId);

        assertAll(
                () -> assertEquals(
                        campaignId,
                        response.campaignId()
                ),
                () -> assertEquals(
                        "RUNNING",
                        response.status()
                ),
                () -> assertEquals(
                        "RUNNING",
                        statusOf(campaignId)
                ),
                () -> assertNotNull(
                        timestampOf(campaignId, "started_at")
                ),
                () -> assertNull(
                        timestampOf(campaignId, "completed_at")
                )
        );

        // Starting enables the campaign.
        // It does not itself send the recipient's email.
        assertEquals(
                "PENDING",
                jdbcTemplate.queryForObject(
                        """
                        SELECT status
                        FROM email_job
                        WHERE campaign_id = ?
                        """,
                        String.class,
                        campaignId
                )
        );
    }

    @Test
    void rejectsRepeatedStartWithoutChangingStartedAt() {

        long campaignId = createCampaign();
        addPendingRecipient(campaignId);

        campaignStartService.start(campaignId);

        Timestamp originalStartedAt =
                timestampOf(campaignId, "started_at");

        assertNotNull(originalStartedAt);

        assertThrows(
                CampaignConflictException.class,
                () -> campaignStartService.start(campaignId)
        );

        assertAll(
                () -> assertEquals(
                        "RUNNING",
                        statusOf(campaignId)
                ),
                () -> assertEquals(
                        originalStartedAt,
                        timestampOf(campaignId, "started_at")
                ),
                () -> assertNull(
                        timestampOf(campaignId, "completed_at")
                )
        );
    }

    private long createCampaign() {

        return campaignRepository.insert(
                "Campaign start integration test",
                "Test subject",
                "Test message"
        );
    }

    private void addPendingRecipient(long campaignId) {

        jdbcTemplate.update(
                """
                INSERT INTO email_job (
                    campaign_id,
                    recipient_email,
                    status,
                    attempt_count
                )
                VALUES (?, ?, 'PENDING', 0)
                """,
                campaignId,
                "start-test@example.com"
        );
    }

    private String statusOf(long campaignId) {

        return jdbcTemplate.queryForObject(
                "SELECT status FROM campaign WHERE id = ?",
                String.class,
                campaignId
        );
    }

    private Timestamp timestampOf(
            long campaignId,
            String column) {

        // Only these two fixed column names are allowed.
        String sql = switch (column) {
            case "started_at" ->
                    "SELECT started_at FROM campaign WHERE id = ?";
            case "completed_at" ->
                    "SELECT completed_at FROM campaign WHERE id = ?";
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported timestamp column"
                    );
        };

        return jdbcTemplate.queryForObject(
                sql,
                Timestamp.class,
                campaignId
        );
    }

    private void assertStillDraft(long campaignId) {

        assertAll(
                () -> assertEquals(
                        "DRAFT",
                        statusOf(campaignId)
                ),
                () -> assertNull(
                        timestampOf(campaignId, "started_at")
                ),
                () -> assertNull(
                        timestampOf(campaignId, "completed_at")
                )
        );
    }
}