package com.email.bulk.importing;

public record EmailValidationResult(
        String normalizedEmail,
        String error
) {

    public boolean isValid() {
        return error == null;
    }
}