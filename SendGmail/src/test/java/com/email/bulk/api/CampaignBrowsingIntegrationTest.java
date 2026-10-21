package com.email.bulk.api;

import com.email.bulk.repository.CampaignRepository;
import com.email.bulk.repository.RecipientImportRepository;
import com.email.bulk.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        locations = "classpath:application-test.properties"
)
@Transactional
class CampaignBrowsingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private RecipientImportRepository importRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long ownerUserId;

    @BeforeEach
    void verifyTestDatabase() {

        assertEquals(
                "bulkemailtest",
                jdbcTemplate.queryForObject(
                        "SELECT DATABASE()",
                        String.class
                )
        );

        ownerUserId = userAccountRepository.insert(
                "Browsing test owner",
                "browsing-" + UUID.randomUUID() + "@example.com",
                "unused-test-password-hash"
        );
    }

    @Test
    void campaignListUsesDescendingIdsAndExclusiveCursor()
            throws Exception {

        long oldest = createCampaign("Browsing oldest");
        long middle = createCampaign("Browsing middle");
        long newest = createCampaign("Browsing newest");

        mockMvc.perform(
                        authenticatedGet("/api/v1/campaigns")
                                .param("limit", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaigns.length()").value(2))
                .andExpect(jsonPath("$.campaigns[0].id")
                        .value(equalTo(newest), Long.class))
                .andExpect(jsonPath("$.campaigns[0].name")
                        .value("Browsing newest"))
                .andExpect(jsonPath("$.campaigns[0].subject")
                        .value("Browsing test subject"))
                .andExpect(jsonPath("$.campaigns[0].status")
                        .value("DRAFT"))
                .andExpect(jsonPath("$.campaigns[1].id")
                        .value(equalTo(middle), Long.class))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextBeforeId")
                        .value(equalTo(middle), Long.class));

        // The cursor record must not appear again on the next page.
        mockMvc.perform(
                        authenticatedGet("/api/v1/campaigns")
                                .param("beforeId", Long.toString(middle))
                                .param("limit", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaigns.length()").value(1))
                .andExpect(jsonPath("$.campaigns[0].id")
                        .value(equalTo(oldest), Long.class))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextBeforeId")
                        .value(equalTo(0L), Long.class));

        // Generated campaign IDs are positive.
        mockMvc.perform(
                        authenticatedGet("/api/v1/campaigns")
                                .param("beforeId", "1")
                                .param("limit", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaigns").isEmpty())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextBeforeId")
                        .value(equalTo(0L), Long.class));
    }

    @Test
    void campaignListRejectsInvalidPagination() throws Exception {

        mockMvc.perform(
                        authenticatedGet("/api/v1/campaigns")
                                .param("beforeId", "-1")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        for (String limit : new String[]{"0", "-1", "101"}) {
            mockMvc.perform(
                            authenticatedGet("/api/v1/campaigns")
                                    .param("limit", limit)
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void importHistoryPaginatesAndExcludesOtherCampaigns()
            throws Exception {

        long campaignId = createCampaign("History target");
        long otherCampaignId = createCampaign("History other");

        long oldest = createImport(campaignId, "oldest.csv");

        createImport(otherCampaignId, "other-first.csv");

        long middle = createImport(campaignId, "middle.csv");

        createImport(otherCampaignId, "other-second.csv");

        long newest = createImport(campaignId, "newest.csv");

        // The highest import ID belongs to the other campaign.
        createImport(otherCampaignId, "other-newest.csv");

        mockMvc.perform(
                        authenticatedGet(
                                "/api/v1/campaigns/{campaignId}/imports",
                                campaignId
                        ).param("limit", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId")
                        .value(equalTo(campaignId), Long.class))
                .andExpect(jsonPath("$.imports.length()").value(2))
                .andExpect(jsonPath("$.imports[0].importId")
                        .value(equalTo(newest), Long.class))
                .andExpect(jsonPath("$.imports[0].campaignId")
                        .value(equalTo(campaignId), Long.class))
                .andExpect(jsonPath("$.imports[0].originalFilename")
                        .value("newest.csv"))
                .andExpect(jsonPath("$.imports[0].status")
                        .value("QUEUED"))
                .andExpect(jsonPath("$.imports[0].processedRows")
                        .value(equalTo(0L), Long.class))
                .andExpect(jsonPath("$.imports[1].importId")
                        .value(equalTo(middle), Long.class))
                .andExpect(jsonPath("$.imports[1].campaignId")
                        .value(equalTo(campaignId), Long.class))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextBeforeId")
                        .value(equalTo(middle), Long.class));

        mockMvc.perform(
                        authenticatedGet(
                                "/api/v1/campaigns/{campaignId}/imports",
                                campaignId
                        )
                                .param("beforeId", Long.toString(middle))
                                .param("limit", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imports.length()").value(1))
                .andExpect(jsonPath("$.imports[0].importId")
                        .value(equalTo(oldest), Long.class))
                .andExpect(jsonPath("$.imports[0].campaignId")
                        .value(equalTo(campaignId), Long.class))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextBeforeId")
                        .value(equalTo(0L), Long.class));

        mockMvc.perform(
                        authenticatedGet(
                                "/api/v1/campaigns/{campaignId}/imports",
                                campaignId
                        )
                                .param("beforeId", Long.toString(oldest))
                                .param("limit", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imports").isEmpty())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextBeforeId")
                        .value(equalTo(0L), Long.class));
    }

    @Test
    void existingCampaignWithoutImportsReturnsEmptyHistory()
            throws Exception {

        long campaignId = createCampaign("Empty history");

        mockMvc.perform(
                        authenticatedGet(
                                "/api/v1/campaigns/{campaignId}/imports",
                                campaignId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId")
                        .value(equalTo(campaignId), Long.class))
                .andExpect(jsonPath("$.imports").isEmpty())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextBeforeId")
                        .value(equalTo(0L), Long.class));
    }

    @Test
    void missingCampaignReturnsNotFoundForImportHistory()
            throws Exception {

        long campaignId = createCampaign("Deleted test campaign");

        assertEquals(
                1,
                jdbcTemplate.update(
                        "DELETE FROM campaign WHERE id = ?",
                        campaignId
                )
        );

        mockMvc.perform(
                        authenticatedGet(
                                "/api/v1/campaigns/{campaignId}/imports",
                                campaignId
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void importHistoryRejectsInvalidPagination() throws Exception {

        long campaignId = createCampaign("History validation");

        mockMvc.perform(
                        authenticatedGet(
                                "/api/v1/campaigns/{campaignId}/imports",
                                campaignId
                        ).param("beforeId", "-1")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        for (String limit : new String[]{"0", "-1", "101"}) {
            mockMvc.perform(
                            authenticatedGet(
                                    "/api/v1/campaigns/{campaignId}/imports",
                                    campaignId
                            ).param("limit", limit)
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void nonNumericParametersReturnBadRequest() throws Exception {

        mockMvc.perform(
                        authenticatedGet("/api/v1/campaigns")
                                .param("beforeId", "abc")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        authenticatedGet("/api/v1/campaigns")
                                .param("limit", "abc")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        authenticatedGet("/api/v1/campaigns/abc/imports")
                )
                .andExpect(status().isBadRequest());

        long campaignId = createCampaign("Numeric validation");

        mockMvc.perform(
                        authenticatedGet(
                                "/api/v1/campaigns/{campaignId}/imports",
                                campaignId
                        ).param("beforeId", "abc")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        authenticatedGet(
                                "/api/v1/campaigns/{campaignId}/imports",
                                campaignId
                        ).param("limit", "abc")
                )
                .andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder authenticatedGet(
            String url,
            Object... uriVariables) {

        return get(url, uriVariables)
                .with(jwt().jwt(token ->
                        token.subject(Long.toString(ownerUserId))
                ));
    }

    private long createCampaign(String name) {

        return campaignRepository.insertOwned(
                ownerUserId,
                name,
                "Browsing test subject",
                "Browsing test body"
        );
    }

    private long createImport(
            long campaignId,
            String originalFilename) {

        // Only metadata is needed; workers are disabled in this profile.
        return importRepository.insert(
                campaignId,
                originalFilename,
                "test-" + UUID.randomUUID() + ".csv",
                100L
        );
    }
}