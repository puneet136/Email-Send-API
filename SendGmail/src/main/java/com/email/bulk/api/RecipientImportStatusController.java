package com.email.bulk.api;

import com.email.bulk.api.dto.RecipientImportStatusResponse;
import com.email.bulk.service.RecipientImportStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/imports")
public class RecipientImportStatusController {

    private final RecipientImportStatusService statusService;

    public RecipientImportStatusController(
            RecipientImportStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/{importId}")
    public RecipientImportStatusResponse getStatus(
            @PathVariable("campaignId") long campaignId,
            @PathVariable("importId") long importId) {

        return statusService.getStatus(campaignId, importId);
    }
}