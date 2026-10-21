package com.email.bulk.api;

import com.email.bulk.repository.CampaignRepository;
import com.email.bulk.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.email.bulk.repository.RecipientImportRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.List;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        locations = "classpath:application-test.properties"
)
@Transactional
class CampaignOwnershipIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private RecipientImportRepository importRepository;

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
    void creationUsesAuthenticatedUserAsOwner() throws Exception {

        long userId = createUser();

        String response = mockMvc.perform(
                        post("/api/v1/campaigns")
                                .with(jwt().jwt(token ->
                                        token.subject(Long.toString(userId))))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of(
                                                "name", "Owned campaign",
                                                "subject", "Test subject",
                                                "message", "Test message"
                                        )
                                ))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long campaignId = objectMapper.readTree(response)
                .get("id")
                .asLong();

        assertEquals(
                userId,
                jdbcTemplate.queryForObject(
                        "SELECT owner_user_id FROM campaign WHERE id = ?",
                        Long.class,
                        campaignId
                ).longValue()
        );
    }

    @Test
    void listingPaginatesOnlyOwnedCampaigns() throws Exception {

        long firstUser = createUser();
        long secondUser = createUser();

        long oldest = ownedCampaign(firstUser, "Oldest");

        ownedCampaign(secondUser, "Other user's campaign");

        long newest = ownedCampaign(firstUser, "Newest");

        // A newer owner-less campaign must also be excluded.
        campaignRepository.insert("Legacy", "Subject", "Body");

        mockMvc.perform(
                        get("/api/v1/campaigns")
                                .with(jwt().jwt(token ->
                                        token.subject(Long.toString(firstUser))))
                                .param("limit", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaigns.length()").value(1))
                .andExpect(jsonPath("$.campaigns[0].id")
                        .value(org.hamcrest.Matchers.equalTo(newest),
                                Long.class))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextBeforeId")
                        .value(org.hamcrest.Matchers.equalTo(newest),
                                Long.class));

        mockMvc.perform(
                        get("/api/v1/campaigns")
                                .with(jwt().jwt(token ->
                                        token.subject(Long.toString(firstUser))))
                                .param("limit", "1")
                                .param("beforeId", Long.toString(newest))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaigns.length()").value(1))
                .andExpect(jsonPath("$.campaigns[0].id")
                        .value(org.hamcrest.Matchers.equalTo(oldest),
                                Long.class))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextBeforeId").value(0));
    }

    @Test
    void detailsAllowOwnerAndRejectOtherUserAndOwnerlessCampaign()
            throws Exception {

        long owner = createUser();
        long otherUser = createUser();

        long campaignId = ownedCampaign(owner, "Private campaign");
        long legacyId = campaignRepository.insert(
                "Legacy",
                "Subject",
                "Body"
        );

        mockMvc.perform(
                        get("/api/v1/campaigns/{id}", campaignId)
                                .with(jwt().jwt(token ->
                                        token.subject(Long.toString(owner))))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Private campaign"));

        mockMvc.perform(
                        get("/api/v1/campaigns/{id}", campaignId)
                                .with(jwt().jwt(token ->
                                        token.subject(Long.toString(otherUser))))
                )
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        get("/api/v1/campaigns/{id}", legacyId)
                                .with(jwt().jwt(token ->
                                        token.subject(Long.toString(owner))))
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnauthenticatedAndDisabledUsers() throws Exception {

        mockMvc.perform(get("/api/v1/campaigns"))
                .andExpect(status().isUnauthorized());

        long userId = createUser();

        jdbcTemplate.update(
                "UPDATE app_user SET enabled = FALSE WHERE id = ?",
                userId
        );

        mockMvc.perform(
                        get("/api/v1/campaigns")
                                .with(jwt().jwt(token ->
                                        token.subject(Long.toString(userId))))
                )
                .andExpect(status().isUnauthorized());
    }

    private long createUser() {

        // Authentication is supplied by Spring Security's test helper.
        // This fixture is never used for password login.
        return userRepository.insert(
                "Ownership test",
                UUID.randomUUID() + "@example.com",
                "unused-test-password-hash"
        );
    }

    private long ownedCampaign(long userId, String name) {
        return campaignRepository.insertOwned(
                userId,
                name,
                "Subject",
                "Body"
        );
    }
    @Test
    void progressAllowsOwnerAndRejectsOtherUser() throws Exception {

        long owner = createUser();
        long otherUser = createUser();
        long campaignId = ownedCampaign(owner, "Progress ownership");

        mockMvc.perform(
                        get("/api/v1/campaigns/{id}/progress", campaignId)
                                .with(asUser(owner))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId")
                        .value(
                                org.hamcrest.Matchers.equalTo(campaignId),
                                Long.class
                        ));

        mockMvc.perform(
                        get("/api/v1/campaigns/{id}/progress", campaignId)
                                .with(asUser(otherUser))
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void importReadsAllowOwnerAndRejectOtherUserAndMismatchedCampaign()
            throws Exception {

        long owner = createUser();
        long otherUser = createUser();

        long campaignId = ownedCampaign(owner, "Import ownership");
        long secondCampaignId = ownedCampaign(owner, "Another owned campaign");

        long importId = importRepository.insert(
                campaignId,
                "ownership-test.csv",
                "test-" + UUID.randomUUID() + ".csv",
                100L
        );

        String base = "/api/v1/campaigns/" + campaignId + "/imports";

        // History, status and errors must all enforce campaign ownership.
        for (String path : List.of(
                base,
                base + "/" + importId,
                base + "/" + importId + "/errors"
        )) {
            mockMvc.perform(get(path).with(asUser(owner)))
                    .andExpect(status().isOk());

            mockMvc.perform(get(path).with(asUser(otherUser)))
                    .andExpect(status().isNotFound());
        }

        // Owning both campaigns must not allow mixing their import IDs.
        String mismatchedBase =
                "/api/v1/campaigns/" + secondCampaignId + "/imports/" + importId;

        mockMvc.perform(
                        get(mismatchedBase).with(asUser(owner))
                )
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        get(mismatchedBase + "/errors").with(asUser(owner))
                )
                .andExpect(status().isNotFound());

        assertEquals(
                1L,
                countImports(campaignId)
        );

        assertEquals(
                0L,
                countImports(secondCampaignId)
        );
    }

    @Test
    void otherUserCannotAddRecipientsOrStartCampaign() throws Exception {

        long owner = createUser();
        long otherUser = createUser();
        long campaignId = ownedCampaign(owner, "Protected writes");

        // A pending recipient makes this campaign otherwise eligible to start.
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
                "existing@example.com"
        );

        mockMvc.perform(
                        post("/api/v1/campaigns/{id}/recipients", campaignId)
                                .with(asUser(otherUser))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of(
                                                "emails",
                                                List.of("unauthorized@example.com")
                                        )
                                ))
                )
                .andExpect(status().isNotFound());

        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM email_job WHERE campaign_id = ?",
                        Long.class,
                        campaignId
                ).longValue()
        );

        mockMvc.perform(
                        post("/api/v1/campaigns/{id}/start", campaignId)
                                .with(asUser(otherUser))
                )
                .andExpect(status().isNotFound());

        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM campaign
                        WHERE id = ?
                          AND owner_user_id = ?
                          AND status = 'DRAFT'
                          AND started_at IS NULL
                          AND completed_at IS NULL
                        """,
                        Long.class,
                        campaignId,
                        owner
                ).longValue()
        );

        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM email_job
                        WHERE campaign_id = ?
                          AND recipient_email = 'existing@example.com'
                          AND status = 'PENDING'
                          AND attempt_count = 0
                        """,
                        Long.class,
                        campaignId
                ).longValue()
        );
    }

    @Test
    void otherUserCannotUploadCsvOrCreateImportRecord() throws Exception {

        long owner = createUser();
        long otherUser = createUser();
        long campaignId = ownedCampaign(owner, "Protected upload");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "ownership-test.csv",
                "text/csv",
                "email\nrecipient@example.com\n"
                        .getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(
                        multipart(
                                "/api/v1/campaigns/{id}/imports",
                                campaignId
                        )
                                .file(file)
                                .with(asUser(otherUser))
                )
                .andExpect(status().isNotFound());

        assertEquals(0L, countImports(campaignId));

        assertEquals(
                0L,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM email_job WHERE campaign_id = ?",
                        Long.class,
                        campaignId
                ).longValue()
        );

        assertEquals(
                "DRAFT",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM campaign WHERE id = ?",
                        String.class,
                        campaignId
                )
        );
    }

    private RequestPostProcessor asUser(long userId) {

        return jwt().jwt(token ->
                token.subject(Long.toString(userId))
        );
    }

    private long countImports(long campaignId) {

        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM recipient_import
                WHERE campaign_id = ?
                """,
                Long.class,
                campaignId
        ).longValue();
    }
}