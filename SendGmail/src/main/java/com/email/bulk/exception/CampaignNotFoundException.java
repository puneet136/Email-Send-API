package com.email.bulk.exception;

public class CampaignNotFoundException extends RuntimeException {

    public CampaignNotFoundException(long id) {
        super("Campaign not found with id: " + id);
    }
}