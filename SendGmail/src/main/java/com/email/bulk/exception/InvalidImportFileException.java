package com.email.bulk.exception;

public class InvalidImportFileException extends RuntimeException {

    public InvalidImportFileException(String message) {
        super(message);
    }
}