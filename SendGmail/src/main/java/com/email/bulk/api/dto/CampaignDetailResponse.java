package com.email.bulk.api.dto;

public record CampaignDetailResponse(
        long id,
        String name,
        String subject,
        String message,
        String status
) {
}