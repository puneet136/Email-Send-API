package com.email.bulk.api.dto;

import java.util.List;

public record RecipientImportHistoryResponse(
        long campaignId,
        List<RecipientImportStatusResponse> imports,
        boolean hasMore,
        long nextBeforeId
) {
}