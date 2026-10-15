package com.email.bulk.api;

import com.email.bulk.api.dto.CampaignProgressResponse;
import com.email.bulk.exception.CampaignNotFoundException;
import com.email.bulk.service.CampaignProgressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CampaignProgressControllerTest {

    private CampaignProgressService progressService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        progressService = mock(CampaignProgressService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new CampaignProgressController(progressService)
                )
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsCompletedCampaignProgress() throws Exception {

        CampaignProgressResponse response =
                new CampaignProgressResponse(
                        3L,
                        "COMPLETED",
                        1100L,
                        0L,
                        0L,
                        0L,
                        1100L,
                        0L,
                        1100L,
                        new BigDecimal("100.00")
                );

        when(progressService.getProgress(3L))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/campaigns/3/progress")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.campaignId").value(3))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalRecipients").value(1100))
                .andExpect(jsonPath("$.pending").value(0))
                .andExpect(jsonPath("$.sending").value(0))
                .andExpect(jsonPath("$.retry").value(0))
                .andExpect(jsonPath("$.sent").value(1100))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.processedRecipients").value(1100))
                .andExpect(jsonPath("$.progressPercent").value(100.0));

        verify(progressService).getProgress(3L);
    }

    @Test
    void returnsZeroCountsForEmptyCampaign() throws Exception {

        CampaignProgressResponse response =
                new CampaignProgressResponse(
                        10L,
                        "DRAFT",
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        new BigDecimal("0.00")
                );

        when(progressService.getProgress(10L))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/campaigns/10/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(10))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalRecipients").value(0))
                .andExpect(jsonPath("$.pending").value(0))
                .andExpect(jsonPath("$.sending").value(0))
                .andExpect(jsonPath("$.retry").value(0))
                .andExpect(jsonPath("$.sent").value(0))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.processedRecipients").value(0))
                .andExpect(jsonPath("$.progressPercent").value(0.0));

        verify(progressService).getProgress(10L);
    }

    @Test
    void returns404WhenCampaignDoesNotExist() throws Exception {

        CampaignNotFoundException exception =
                new CampaignNotFoundException(999L);

        when(progressService.getProgress(999L))
                .thenThrow(exception);

        mockMvc.perform(get("/api/v1/campaigns/999/progress"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value(exception.getMessage()));

        verify(progressService).getProgress(999L);
    }

    @Test
    void rejectsNonNumericCampaignId() throws Exception {

        mockMvc.perform(get("/api/v1/campaigns/abc/progress"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(progressService);
    }
}