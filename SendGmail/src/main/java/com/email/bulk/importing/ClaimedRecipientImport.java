package com.email.bulk.importing;

public record ClaimedRecipientImport(
        long importId,
        long campaignId,
        String storedFilename,
        long processedRows,
        String workerToken
) {
}