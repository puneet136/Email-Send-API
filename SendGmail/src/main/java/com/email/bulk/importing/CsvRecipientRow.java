package com.email.bulk.importing;

public record CsvRecipientRow(
        long recordNumber,
        String email,
        String error
) {
}