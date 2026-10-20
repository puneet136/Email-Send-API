package com.email.bulk.api.dto;

public record UserResponse(
        long id,
        String displayName,
        String email
) {
}