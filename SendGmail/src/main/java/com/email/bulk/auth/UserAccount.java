package com.email.bulk.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record UserAccount(
        long id,
        String displayName,
        String email,

        @JsonIgnore
        String passwordHash,
        boolean enabled
) {

    @Override
    public String toString() {
        return "UserAccount[id=" + id
                + ", enabled=" + enabled + "]";
    }
}