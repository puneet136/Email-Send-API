package com.email.bulk.api.dto;

public record RecipientImportStatusResponse(
        long importId,
        long campaignId,
        String originalFilename,
        String status,
        long processedRows,
        long addedRows,
        long duplicateRows,
        long invalidRows,
        String lastError
) {
}