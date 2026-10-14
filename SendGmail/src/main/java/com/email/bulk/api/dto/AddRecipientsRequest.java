package com.email.bulk.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AddRecipientsRequest(

        @NotEmpty(message = "At least one email is required")
        @Size(
                max = 500,
                message = "Maximum 500 emails are allowed per request"
        )
        List<
                @NotBlank(message = "Email must not be blank")
                @Email(message = "Email format is invalid")
                @Size(
                        max = 254,
                        message = "Email must not exceed 254 characters"
                )
                        String
                > emails

) {
}