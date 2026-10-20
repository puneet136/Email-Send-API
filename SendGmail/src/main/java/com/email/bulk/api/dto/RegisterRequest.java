package com.email.bulk.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank
        @Size(max = 100)
        String displayName,

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(min = 15, max = 128)
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String password

) {

    @Override
    public String toString() {
        return "RegisterRequest[credentials=REDACTED]";
    }
}