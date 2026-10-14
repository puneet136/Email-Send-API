package com.email.bulk.importing;

public record ImportRowError(
        long recordNumber,
        String rawValue,
        String reason
) {
}