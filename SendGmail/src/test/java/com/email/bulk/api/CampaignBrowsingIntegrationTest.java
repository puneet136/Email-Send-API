package com.email.bulk.api;

import com.email.bulk.repository.CampaignRepository;
import com.email.bulk.repository.RecipientImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void campaignListUsesDescendingIdsAndExclusiveCursor()
            throws Exception {

        long oldest = createCampaign("Browsing oldest");
        long middle = createCampaign("Browsing middle");
        long newest = createCampaign("Browsing newest");

        mockMvc.perform(
                        get("/api/v1/campaigns")
                                .param("limit", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaigns.length()").value(2))
                .andExpect(jsonPath("$.campaigns[0].id")
                        .value(org.hamcrest.Matchers.equalTo(newest), Long.class))
                .andExpect(jsonPath("$.campaigns[0].name")
                        .value("Browsing newest"))
                .andExpect(jsonPath("$.campaigns[0].subject")
                        .value("Browsing test subject"))
                .andExpect(jsonPath("$.campaigns[0].status")
                        .value("DRAFT"))
                .andExpect(jsonPath("$.campaigns[1].id")
                        .value(org.hamcrest.Matchers.equalTo(middle), Long.class))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextBeforeId")
                        .value(org.hamcrest.Matchers.equalTo(middle), Long.class));

        // Cursor record must not appear again on the next page.
        // Older pre-existing test records may follow our oldest record.
        mockMvc.perform(
                        get("/api/v1/campaigns")
                                .param("beforeId", Long.toString(middle))
                                .param("limit", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaigns.length()").value(1))
                .andExpect(jsonPath("$.campaigns[0].id")
                        .value(org.hamcrest.Matchers.equalTo(oldest), Long.class));

        // Generated campaign IDs are positive, so nothing precedes ID 1.
        mockMvc.perform(
                        get("/api/v1/campaigns")
                                .param("beforeId", "1")
                                .param("limit", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaigns").isEmpty())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextBeforeId")
                        .value(org.hamcrest.Matchers.equalTo(0L), Long.class));
    }

    @Test
    void campaignListRejectsInvalidPagination() throws Exception {

        mockMvc.perform(
                        get("/api/v1/campaigns")
                                .param("beforeId", "-1")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        for (String limit : new String[]{"0", "-1", "101"}) {

            mockMvc.perform(
                            get("/api/v1/campaigns")
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

        // Make the highest import ID belong to the other campaign.
        createImport(otherCampaignId, "other-newest.csv");

        mockMvc.perform(
                        get("/api/v1/campaigns/{campaignId}/imports",
                                campaignId)
                                .param("limit", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId")
                        .value(org.hamcrest.Matchers.equalTo(campaignId), Long.class))
                .andExpect(jsonPath("$.imports.length()").value(2))
                .andExpect(jsonPath("$.imports[0].importId")
                        .value(org.hamcrest.Matchers.equalTo(newest), Long.class))
                .andExpect(jsonPath("$.imports[0].campaignId")
                        .value(org.hamcrest.Matchers.equalTo(campaignId), Long.class))
                .andExpect(jsonPath("$.imports[0].originalFilename")
                        .value("newest.csv"))
                .andExpect(jsonPath("$.imports[0].status")
                        .value("QUEUED"))
                .andExpect(jsonPath("$.imports[0].processedRows")
                        .value(org.hamcrest.Matchers.equalTo(0L), Long.class))
                .andExpect(jsonPath("$.imports[1].importId")
                        .value(org.hamcrest.Matchers.equalTo(middle), Long.class))
                .andExpect(jsonPath("$.imports[1].campaignId")
                        .value(org.hamcrest.Matchers.equalTo(campaignId), Long.class))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextBeforeId")
                        .value(org.hamcrest.Matchers.equalTo(middle), Long.class));

        mockMvc.perform(
                        get("/api/v1/campaigns/{campaignId}/imports",
                                campaignId)
                                .param("beforeId", Long.toString(middle))
                                .param("limit", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imports.length()").value(1))
                .andExpect(jsonPath("$.imports[0].importId")
                        .value(org.hamcrest.Matchers.equalTo(oldest), Long.class))
                .andExpect(jsonPath("$.imports[0].campaignId")
                        .value(org.hamcrest.Matchers.equalTo(campaignId), Long.class))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextBeforeId")
                        .value(org.hamcrest.Matchers.equalTo(0L), Long.class));

        mockMvc.perform(
                        get("/api/v1/campaigns/{campaignId}/imports",
                                campaignId)
                                .param("beforeId", Long.toString(oldest))
                                .param("limit", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imports").isEmpty())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextBeforeId")
                        .value(org.hamcrest.Matchers.equalTo(0L), Long.class));
    }

    @Test
    void existingCampaignWithoutImportsReturnsEmptyHistory()
            throws Exception {

        long campaignId = createCampaign("Empty history");

        mockMvc.perform(
                        get("/api/v1/campaigns/{campaignId}/imports",
                                campaignId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId")
                        .value(org.hamcrest.Matchers.equalTo(campaignId), Long.class))
                .andExpect(jsonPath("$.imports").isEmpty())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextBeforeId")
                        .value(org.hamcrest.Matchers.equalTo(0L), Long.class));
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
                        get("/api/v1/campaigns/{campaignId}/imports",
                                campaignId)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void importHistoryRejectsInvalidPagination() throws Exception {

        long campaignId = createCampaign("History validation");

        mockMvc.perform(
                        get("/api/v1/campaigns/{campaignId}/imports",
                                campaignId)
                                .param("beforeId", "-1")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        for (String limit : new String[]{"0", "-1", "101"}) {

            mockMvc.perform(
                            get("/api/v1/campaigns/{campaignId}/imports",
                                    campaignId)
                                    .param("limit", limit)
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void nonNumericParametersReturnBadRequest() throws Exception {

        mockMvc.perform(
                        get("/api/v1/campaigns")
                                .param("beforeId", "abc")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/v1/campaigns")
                                .param("limit", "abc")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/v1/campaigns/abc/imports")
                )
                .andExpect(status().isBadRequest());

        long campaignId = createCampaign("Numeric validation");

        mockMvc.perform(
                        get("/api/v1/campaigns/{campaignId}/imports",
                                campaignId)
                                .param("beforeId", "abc")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/v1/campaigns/{campaignId}/imports",
                                campaignId)
                                .param("limit", "abc")
                )
                .andExpect(status().isBadRequest());
    }

    private long createCampaign(String name) {

        return campaignRepository.insert(
                name,
                "Browsing test subject",
                "Browsing test body"
        );
    }

    private long createImport(
            long campaignId,
            String originalFilename) {

        // Only database metadata is needed for these read API tests.
        // Background workers are disabled in the test profile.
        return importRepository.insert(
                campaignId,
                originalFilename,
                "test-" + UUID.randomUUID() + ".csv",
                100L
        );
    }
}