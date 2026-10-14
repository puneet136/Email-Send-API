package com.email.bulk.repository;

import com.email.bulk.sending.ClaimedEmailJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EmailDeliveryRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmailDeliveryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedEmailJob> claimNext(
            int leaseSeconds,
            int maxAttempts) {

        if (leaseSeconds < 30 || leaseSeconds > 3600) {
            throw new IllegalArgumentException(
                    "Lease duration must be between 30 and 3600 seconds"
            );
        }

        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException(
                    "Maximum attempts must be between 1 and 10"
            );
        }

        String workerToken = UUID.randomUUID().toString();

        String selectSql = """
                SELECT
                    j.id,
                    j.campaign_id,
                    j.recipient_email,
                    j.attempt_count,
                    c.subject,
                    c.body
                FROM email_job j
                JOIN campaign c ON c.id = j.campaign_id
                WHERE c.status = 'RUNNING'
                  AND j.status IN ('PENDING', 'RETRY')
                  AND j.next_attempt_at <= CURRENT_TIMESTAMP(6)
                  AND j.attempt_count < ?
                ORDER BY j.next_attempt_at, j.id
                LIMIT 1
                """;

        List<ClaimedEmailJob> candidates = jdbcTemplate.query(
                selectSql,
                (rs, rowNumber) -> new ClaimedEmailJob(
                        rs.getLong("id"),
                        rs.getLong("campaign_id"),
                        rs.getString("recipient_email"),
                        rs.getString("subject"),
                        rs.getString("body"),
                        rs.getInt("attempt_count") + 1,
                        workerToken
                ),
                maxAttempts
        );

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        ClaimedEmailJob candidate = candidates.get(0);

        String claimSql = """
                UPDATE email_job j
                JOIN campaign c ON c.id = j.campaign_id
                SET j.status = 'SENDING',
                    j.worker_token = ?,
                    j.lease_expires_at = TIMESTAMPADD(
                        SECOND, ?, CURRENT_TIMESTAMP(6)
                    ),
                    j.last_attempt_at = CURRENT_TIMESTAMP(6),
                    j.attempt_count = j.attempt_count + 1
                WHERE j.id = ?
                  AND j.campaign_id = ?
                  AND c.status = 'RUNNING'
                  AND j.status IN ('PENDING', 'RETRY')
                  AND j.next_attempt_at <= CURRENT_TIMESTAMP(6)
                  AND j.attempt_count = ?
                  AND j.attempt_count < ?
                """;

        int claimed = jdbcTemplate.update(
                claimSql,
                candidate.workerToken(),
                leaseSeconds,
                candidate.jobId(),
                candidate.campaignId(),
                candidate.attemptCount() - 1,
                maxAttempts
        );

        if (claimed == 0) {
            return Optional.empty();
        }

        if (claimed != 1) {
            throw new IllegalStateException(
                    "Unexpected email job claim result"
            );
        }

        return Optional.of(candidate);
    }

    private void validateWork(ClaimedEmailJob work) {

        if (work == null) {
            throw new IllegalArgumentException(
                    "Claimed email job must not be null"
            );
        }

        if (work.workerToken() == null
                || work.workerToken().isBlank()) {
            throw new IllegalArgumentException(
                    "Worker token must not be blank"
            );
        }

        if (work.attemptCount() < 1) {
            throw new IllegalArgumentException(
                    "Claimed attempt count must be positive"
            );
        }
    }

    private void validateRetrySettings(
            int maxAttempts,
            int retryDelaySeconds) {

        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException(
                    "Maximum attempts must be between 1 and 10"
            );
        }

        if (retryDelaySeconds < 1 || retryDelaySeconds > 86400) {
            throw new IllegalArgumentException(
                    "Retry delay must be between 1 and 86400 seconds"
            );
        }
    }

    private boolean updatedOneOrNone(int updated) {

        if (updated == 0) {
            return false;
        }

        if (updated != 1) {
            throw new IllegalStateException(
                    "Unexpected email job update count"
            );
        }

        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markSent(ClaimedEmailJob work) {

        validateWork(work);

        String sql = """
            UPDATE email_job
            SET status = 'SENT',
                sent_at = CURRENT_TIMESTAMP(6),
                last_error = NULL,
                worker_token = NULL,
                lease_expires_at = NULL
            WHERE id = ?
              AND campaign_id = ?
              AND status = 'SENDING'
              AND worker_token = ?
              AND attempt_count = ?
              AND lease_expires_at > CURRENT_TIMESTAMP(6)
            """;

        int updated = jdbcTemplate.update(
                sql,
                work.jobId(),
                work.campaignId(),
                work.workerToken(),
                work.attemptCount()
        );

        return updatedOneOrNone(updated);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(
            ClaimedEmailJob work,
            String errorMessage,
            int maxAttempts,
            int retryDelaySeconds) {

        validateWork(work);
        validateRetrySettings(maxAttempts, retryDelaySeconds);

        String message = errorMessage;

        if (message == null || message.isBlank()) {
            message = "Email sending attempt failed";
        }

        if (message.codePointCount(0, message.length()) > 1000) {
            int endIndex = message.offsetByCodePoints(0, 1000);
            message = message.substring(0, endIndex);
        }

        String sql = """
            UPDATE email_job
            SET status = CASE
                    WHEN attempt_count >= ? THEN 'FAILED'
                    ELSE 'RETRY'
                END,
                next_attempt_at = TIMESTAMPADD(
                    SECOND, ?, CURRENT_TIMESTAMP(6)
                ),
                last_error = ?,
                worker_token = NULL,
                lease_expires_at = NULL
            WHERE id = ?
              AND campaign_id = ?
              AND status = 'SENDING'
              AND worker_token = ?
              AND attempt_count = ?
              AND lease_expires_at > CURRENT_TIMESTAMP(6)
            """;

        int updated = jdbcTemplate.update(
                sql,
                maxAttempts,
                retryDelaySeconds,
                message,
                work.jobId(),
                work.campaignId(),
                work.workerToken(),
                work.attemptCount()
        );

        return updatedOneOrNone(updated);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverExpiredJobs(
            int maxAttempts,
            int retryDelaySeconds) {

        validateRetrySettings(maxAttempts, retryDelaySeconds);

        String sql = """
            UPDATE email_job
            SET status = CASE
                    WHEN attempt_count >= ? THEN 'FAILED'
                    ELSE 'RETRY'
                END,
                next_attempt_at = TIMESTAMPADD(
                    SECOND, ?, CURRENT_TIMESTAMP(6)
                ),
                last_error =
                    'Sending lease expired; SMTP outcome may be unknown',
                worker_token = NULL,
                lease_expires_at = NULL
            WHERE status = 'SENDING'
              AND lease_expires_at <= CURRENT_TIMESTAMP(6)
            ORDER BY lease_expires_at, id
            LIMIT 100
            """;

        int recovered = jdbcTemplate.update(
                sql,
                maxAttempts,
                retryDelaySeconds
        );

        if (recovered < 0 || recovered > 100) {
            throw new IllegalStateException(
                    "Unexpected expired email job recovery count"
            );
        }

        return recovered;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int completeFinishedCampaigns() {

        String sql = """
            UPDATE campaign c
            SET status = CASE
                    WHEN EXISTS (
                        SELECT 1
                        FROM email_job failed_job
                        WHERE failed_job.campaign_id = c.id
                          AND failed_job.status = 'FAILED'
                    )
                    THEN 'COMPLETED_WITH_ERRORS'
                    ELSE 'COMPLETED'
                END,
                completed_at = CURRENT_TIMESTAMP(6)
            WHERE c.status = 'RUNNING'
              AND EXISTS (
                    SELECT 1
                    FROM email_job any_job
                    WHERE any_job.campaign_id = c.id
              )
              AND NOT EXISTS (
                    SELECT 1
                    FROM email_job unfinished_job
                    WHERE unfinished_job.campaign_id = c.id
                      AND unfinished_job.status NOT IN ('SENT', 'FAILED')
              )
            ORDER BY c.id
            LIMIT 100
            """;

        int completed = jdbcTemplate.update(sql);

        if (completed < 0 || completed > 100) {
            throw new IllegalStateException(
                    "Unexpected campaign completion count"
            );
        }

        return completed;
    }

}