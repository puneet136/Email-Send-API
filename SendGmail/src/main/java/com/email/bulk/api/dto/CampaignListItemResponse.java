package com.email.bulk.api.dto;

public record CampaignListItemResponse(
        long id,
        String name,
        String subject,
        String status
) {
}