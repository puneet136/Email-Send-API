package com.email.bulk.api;

import com.email.bulk.api.dto.CampaignProgressResponse;
import com.email.bulk.service.CampaignProgressService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignProgressController {

    private final CampaignProgressService progressService;

    public CampaignProgressController(
            CampaignProgressService progressService) {

        this.progressService = progressService;
    }

    @GetMapping("/{campaignId}/progress")
    public ResponseEntity<CampaignProgressResponse> getProgress(
            @PathVariable("campaignId") long campaignId) {

        return ResponseEntity.ok(
                progressService.getProgress(campaignId)
        );
    }
}