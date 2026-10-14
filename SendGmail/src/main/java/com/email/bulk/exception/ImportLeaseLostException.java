package com.email.bulk.exception;

public class ImportLeaseLostException extends RuntimeException {

    public ImportLeaseLostException(long importId) {
        super("Worker ownership lost or lease expired for import "
                + importId);
    }
}