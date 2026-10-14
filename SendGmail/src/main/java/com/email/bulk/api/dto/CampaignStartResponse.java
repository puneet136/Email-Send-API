package com.email.bulk.api.dto;

public record CampaignStartResponse(
        long campaignId,
        String status
) {
}