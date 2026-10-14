package com.email.bulk.api.dto;

public record RecipientImportErrorResponse(
        long recordNumber,
        String rawValue,
        String reason
) {
}