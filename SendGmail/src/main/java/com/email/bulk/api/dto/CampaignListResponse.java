package com.email.bulk.api.dto;

import java.util.List;

public record CampaignListResponse(
        List<CampaignListItemResponse> campaigns,
        boolean hasMore,
        long nextBeforeId
) {
}