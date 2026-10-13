package com.email.bulk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCampaignRequest(

        @NotBlank(message = "Campaign name is required")
        @Size(max = 150, message = "Name must not exceed 150 characters")
        String name,

        @NotBlank(message = "Subject is required")
        @Size(max = 200, message = "Subject must not exceed 200 characters")
        String subject,

        @NotBlank(message = "Message is required")
        @Size(max = 10000, message = "Message must not exceed 10000 characters")
        String message

) {
}