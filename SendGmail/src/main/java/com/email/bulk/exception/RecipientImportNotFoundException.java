package com.email.bulk.exception;

public class RecipientImportNotFoundException
        extends RuntimeException {

    public RecipientImportNotFoundException(
            long campaignId,
            long importId) {

        super(
                "Import " + importId
                        + " not found for campaign " + campaignId
        );
    }
}