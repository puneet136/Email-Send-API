package com.email.bulk.api.dto;

import java.util.List;

public record RecipientImportErrorPageResponse(
        long importId,
        List<RecipientImportErrorResponse> errors,
        boolean hasMore,
        long nextAfterRecord
) {
}