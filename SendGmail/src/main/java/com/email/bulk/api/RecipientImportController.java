package com.email.bulk.api;

import com.email.bulk.api.dto.RecipientImportResponse;
import com.email.bulk.service.RecipientImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/imports")
public class RecipientImportController {

    private final RecipientImportService importService;

    public RecipientImportController(
            RecipientImportService importService) {

        this.importService = importService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecipientImportResponse> uploadCsv(
            @PathVariable("campaignId") long campaignId,
            @RequestParam("file") MultipartFile file)
            throws IOException {

        RecipientImportResponse response =
                importService.uploadCsv(campaignId, file);

        return ResponseEntity.accepted().body(response);
    }
}