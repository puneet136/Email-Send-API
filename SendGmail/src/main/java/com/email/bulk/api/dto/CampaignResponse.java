package com.email.bulk.api.dto;

public record CampaignResponse(
        long id,
        String name,
        String status
) {
}