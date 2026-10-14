package com.email.bulk.sending;

public record ClaimedEmailJob(
        long jobId,
        long campaignId,
        String recipientEmail,
        String subject,
        String body,
        int attemptCount,
        String workerToken
) {
}