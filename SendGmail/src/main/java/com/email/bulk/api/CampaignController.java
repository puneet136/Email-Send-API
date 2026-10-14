package com.email.bulk.api;

import com.email.bulk.api.dto.AddRecipientsRequest;
import com.email.bulk.api.dto.AddRecipientsResponse;
import com.email.bulk.api.dto.CampaignDetailResponse;
import com.email.bulk.api.dto.CampaignResponse;
import com.email.bulk.api.dto.CreateCampaignRequest;
import com.email.bulk.service.CampaignRecipientService;
import com.email.bulk.service.CampaignService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignController {

    private final CampaignService campaignService;
    private final CampaignRecipientService campaignRecipientService;

    public CampaignController(
            CampaignService campaignService,
            CampaignRecipientService campaignRecipientService) {

        this.campaignService = campaignService;
        this.campaignRecipientService = campaignRecipientService;
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

    @PostMapping("/{id}/recipients")
    public ResponseEntity<AddRecipientsResponse> addRecipients(
            @PathVariable("id") long id,
            @Valid @RequestBody AddRecipientsRequest request) {

        return ResponseEntity.ok(
                campaignRecipientService.addRecipients(id, request)
        );
    }
}