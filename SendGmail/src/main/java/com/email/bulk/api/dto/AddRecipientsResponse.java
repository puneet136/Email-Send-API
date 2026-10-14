package com.email.bulk.api.dto;

public record AddRecipientsResponse(
        long campaignId,
        int requested,
        int added,
        int duplicates
) {
}