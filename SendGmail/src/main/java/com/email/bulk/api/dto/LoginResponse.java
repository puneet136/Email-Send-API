package com.email.bulk.api.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {

    @Override
    public String toString() {
        return "LoginResponse[token=REDACTED]";
    }
}