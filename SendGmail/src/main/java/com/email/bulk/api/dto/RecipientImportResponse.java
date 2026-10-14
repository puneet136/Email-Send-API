package com.email.bulk.api.dto;

public record RecipientImportResponse(
        long importId,
        long campaignId,
        String status
) {
}