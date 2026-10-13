package com.email.bulk.api;

import com.email.bulk.api.dto.CampaignResponse;
import com.email.bulk.api.dto.CreateCampaignRequest;
import com.email.bulk.service.CampaignService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.email.bulk.api.dto.CampaignDetailResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping
    public ResponseEntity<CampaignResponse> create(
            @Valid @RequestBody CreateCampaignRequest request) {

        CampaignResponse response = campaignService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignDetailResponse> getById(
            @PathVariable("id") long id) {

        return ResponseEntity.ok(campaignService.getById(id));
    }
}