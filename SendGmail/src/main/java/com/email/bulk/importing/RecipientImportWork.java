package com.email.bulk.importing;

public record RecipientImportWork(
        long importId,
        long campaignId,
        String storedFilename,
        long processedRows
) {
}