package com.email.bulk.api;

import com.email.bulk.api.dto.RecipientImportErrorPageResponse;
import com.email.bulk.service.RecipientImportErrorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/imports")
public class RecipientImportErrorController {

    private final RecipientImportErrorService errorService;

    public RecipientImportErrorController(
            RecipientImportErrorService errorService) {

        this.errorService = errorService;
    }

    @GetMapping("/{importId}/errors")
    public RecipientImportErrorPageResponse getErrors(
            @PathVariable("campaignId") long campaignId,
            @PathVariable("importId") long importId,
            @RequestParam(
                    name = "afterRecord",
                    defaultValue = "0"
            ) long afterRecord,
            @RequestParam(
                    name = "limit",
                    defaultValue = "50"
            ) int limit) {

        return errorService.getErrors(
                campaignId,
                importId,
                afterRecord,
                limit
        );
    }
}