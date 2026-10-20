package com.email.bulk.exception;

public class RegistrationConflictException extends RuntimeException {

    public RegistrationConflictException() {
        super("An account with this email already exists");
    }
}