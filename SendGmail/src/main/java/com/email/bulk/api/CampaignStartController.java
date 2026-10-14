package com.email.bulk.api;

import com.email.bulk.api.dto.CampaignStartResponse;
import com.email.bulk.service.CampaignStartService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignStartController {

    private final CampaignStartService campaignStartService;

    public CampaignStartController(
            CampaignStartService campaignStartService) {

        this.campaignStartService = campaignStartService;
    }

    @PostMapping("/{campaignId}/start")
    public ResponseEntity<CampaignStartResponse> start(
            @PathVariable("campaignId") long campaignId) {

        return ResponseEntity.accepted().body(
                campaignStartService.start(campaignId)
        );
    }
}