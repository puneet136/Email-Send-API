package com.email.bulk.api.dto;

import java.math.BigDecimal;

public record CampaignProgressResponse(
        long campaignId,
        String status,
        long totalRecipients,
        long pending,
        long sending,
        long retry,
        long sent,
        long failed,
        long processedRecipients,
        BigDecimal progressPercent
) {
}