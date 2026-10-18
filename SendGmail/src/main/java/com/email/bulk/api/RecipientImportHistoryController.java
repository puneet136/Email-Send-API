package com.email.bulk.api;

import com.email.bulk.api.dto.RecipientImportHistoryResponse;
import com.email.bulk.service.RecipientImportHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/imports")
public class RecipientImportHistoryController {

    private final RecipientImportHistoryService historyService;

    public RecipientImportHistoryController(
            RecipientImportHistoryService historyService) {

        this.historyService = historyService;
    }

    @GetMapping
    public ResponseEntity<RecipientImportHistoryResponse> getHistory(
            @PathVariable("campaignId") long campaignId,
            @RequestParam(name = "beforeId", defaultValue = "0")
            long beforeId,
            @RequestParam(name = "limit", defaultValue = "20")
            int limit) {

        return ResponseEntity.ok(
                historyService.getHistory(
                        campaignId,
                        beforeId,
                        limit
                )
        );
    }
}