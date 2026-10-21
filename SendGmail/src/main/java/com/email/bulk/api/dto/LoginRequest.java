package com.email.bulk.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginRequest(
        String email,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String password
) {

    @Override
    public String toString() {
        return "LoginRequest[credentials=REDACTED]";
    }
}