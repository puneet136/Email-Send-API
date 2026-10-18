package com.email.bulk.repository;

import com.email.bulk.api.dto.RecipientImportStatusResponse;
import com.email.bulk.exception.ImportLeaseLostException;
import com.email.bulk.importing.ClaimedRecipientImport;
import com.email.bulk.importing.RecipientImportWork;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RecipientImportRepository {

    private final JdbcTemplate jdbcTemplate;

    public RecipientImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(
            long campaignId,
            String originalFilename,
            String storedFilename,
            long fileSizeBytes) {

        String sql = """
                INSERT INTO recipient_import (
                    campaign_id,
                    original_filename,
                    stored_filename,
                    file_type,
                    file_size_bytes,
                    status
                )
                VALUES (?, ?, ?, 'CSV', ?, 'QUEUED')
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        int rowsInserted = jdbcTemplate.update(connection -> {

            PreparedStatement statement = connection.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            );

            statement.setLong(1, campaignId);
            statement.setString(2, originalFilename);
            statement.setString(3, storedFilename);
            statement.setLong(4, fileSizeBytes);

            return statement;

        }, keyHolder);

        Number generatedId = keyHolder.getKey();

        if (rowsInserted != 1 || generatedId == null) {
            throw new IllegalStateException(
                    "Import creation did not return an ID"
            );
        }

        return generatedId.longValue();
    }

    public Optional<RecipientImportStatusResponse> findStatus(
            long campaignId,
            long importId) {

        String sql = """
                SELECT
                    id,
                    campaign_id,
                    original_filename,
                    status,
                    processed_rows,
                    added_rows,
                    duplicate_rows,
                    invalid_rows,
                    last_error
                FROM recipient_import
                WHERE campaign_id = ?
                  AND id = ?
                """;

        List<RecipientImportStatusResponse> results =
                jdbcTemplate.query(
                        sql,
                        (rs, rowNumber) ->
                                new RecipientImportStatusResponse(
                                        rs.getLong("id"),
                                        rs.getLong("campaign_id"),
                                        rs.getString("original_filename"),
                                        rs.getString("status"),
                                        rs.getLong("processed_rows"),
                                        rs.getLong("added_rows"),
                                        rs.getLong("duplicate_rows"),
                                        rs.getLong("invalid_rows"),
                                        rs.getString("last_error")
                                ),
                        campaignId,
                        importId
                );

        return results.stream().findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedRecipientImport> claimNextAvailableWithLease(
            int leaseSeconds) {

        if (leaseSeconds < 30 || leaseSeconds > 3600) {
            throw new IllegalArgumentException(
                    "Lease duration must be between 30 and 3600 seconds"
            );
        }

        String selectSql = """
                SELECT id, campaign_id, stored_filename, processed_rows
                FROM recipient_import
                WHERE file_type = 'CSV'
                  AND (
                        status = 'QUEUED'
                        OR (
                            status = 'RUNNING'
                            AND lease_expires_at <= CURRENT_TIMESTAMP(6)
                        )
                  )
                ORDER BY id
                LIMIT 1
                """;

        List<RecipientImportWork> candidates = jdbcTemplate.query(
                selectSql,
                (rs, rowNumber) -> new RecipientImportWork(
                        rs.getLong("id"),
                        rs.getLong("campaign_id"),
                        rs.getString("stored_filename"),
                        rs.getLong("processed_rows")
                )
        );

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        RecipientImportWork candidate = candidates.get(0);
        String workerToken = UUID.randomUUID().toString();

        String claimSql = """
                UPDATE recipient_import
                SET status = 'RUNNING',
                    worker_token = ?,
                    heartbeat_at = CURRENT_TIMESTAMP(6),
                    lease_expires_at = TIMESTAMPADD(
                        SECOND, ?, CURRENT_TIMESTAMP(6)
                    ),
                    claim_count = claim_count + 1,
                    started_at = COALESCE(
                        started_at, CURRENT_TIMESTAMP(6)
                    ),
                    completed_at = NULL,
                    last_error = NULL
                WHERE id = ?
                  AND file_type = 'CSV'
                  AND processed_rows = ?
                  AND (
                        status = 'QUEUED'
                        OR (
                            status = 'RUNNING'
                            AND lease_expires_at <= CURRENT_TIMESTAMP(6)
                        )
                  )
                """;

        int claimed = jdbcTemplate.update(
                claimSql,
                workerToken,
                leaseSeconds,
                candidate.importId(),
                candidate.processedRows()
        );

        if (claimed == 0) {
            return Optional.empty();
        }

        if (claimed != 1) {
            throw new IllegalStateException(
                    "Unexpected import claim result"
            );
        }

        return Optional.of(
                new ClaimedRecipientImport(
                        candidate.importId(),
                        candidate.campaignId(),
                        candidate.storedFilename(),
                        candidate.processedRows(),
                        workerToken
                )
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockActiveLease(
            long importId,
            long campaignId,
            String workerToken,
            long expectedProcessedRows) {

        if (workerToken == null || workerToken.isBlank()) {
            throw new IllegalArgumentException(
                    "Worker token must not be blank"
            );
        }

        if (expectedProcessedRows < 0) {
            throw new IllegalArgumentException(
                    "Expected processed rows must not be negative"
            );
        }

        String sql = """
                SELECT id
                FROM recipient_import
                WHERE id = ?
                  AND campaign_id = ?
                  AND status = 'RUNNING'
                  AND worker_token = ?
                  AND processed_rows = ?
                  AND lease_expires_at > CURRENT_TIMESTAMP(6)
                FOR UPDATE
                """;

        List<Long> matches = jdbcTemplate.query(
                sql,
                (rs, rowNumber) -> rs.getLong("id"),
                importId,
                campaignId,
                workerToken,
                expectedProcessedRows
        );

        if (matches.size() != 1) {
            throw new ImportLeaseLostException(importId);
        }
    }

    public void recordBatchProgress(
            long importId,
            long campaignId,
            long expectedProcessedRows,
            int processed,
            int added,
            int duplicates,
            int invalid) {

        String sql = """
                UPDATE recipient_import
                SET processed_rows = processed_rows + ?,
                    added_rows = added_rows + ?,
                    duplicate_rows = duplicate_rows + ?,
                    invalid_rows = invalid_rows + ?
                WHERE id = ?
                  AND campaign_id = ?
                  AND status = 'RUNNING'
                  AND processed_rows = ?
                """;

        int updated = jdbcTemplate.update(
                sql,
                processed,
                added,
                duplicates,
                invalid,
                importId,
                campaignId,
                expectedProcessedRows
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Import progress changed or import is not running"
            );
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void renewActiveLease(
            long importId,
            long campaignId,
            String workerToken,
            int leaseSeconds) {

        if (workerToken == null || workerToken.isBlank()) {
            throw new IllegalArgumentException(
                    "Worker token must not be blank"
            );
        }

        if (leaseSeconds < 30 || leaseSeconds > 3600) {
            throw new IllegalArgumentException(
                    "Lease duration must be between 30 and 3600 seconds"
            );
        }

        String sql = """
                UPDATE recipient_import
                SET heartbeat_at = CURRENT_TIMESTAMP(6),
                    lease_expires_at = TIMESTAMPADD(
                        SECOND, ?, CURRENT_TIMESTAMP(6)
                    )
                WHERE id = ?
                  AND campaign_id = ?
                  AND status = 'RUNNING'
                  AND worker_token = ?
                  AND lease_expires_at > CURRENT_TIMESTAMP(6)
                """;

        int updated = jdbcTemplate.update(
                sql,
                leaseSeconds,
                importId,
                campaignId,
                workerToken
        );

        if (updated != 1) {
            throw new ImportLeaseLostException(importId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompletedWithLease(
            long importId,
            long campaignId,
            String workerToken,
            long expectedProcessedRows) {

        if (workerToken == null || workerToken.isBlank()) {
            throw new IllegalArgumentException(
                    "Worker token must not be blank"
            );
        }

        if (expectedProcessedRows < 0) {
            throw new IllegalArgumentException(
                    "Expected processed rows must not be negative"
            );
        }

        String sql = """
                UPDATE recipient_import
                SET status = 'COMPLETED',
                    completed_at = CURRENT_TIMESTAMP(6),
                    last_error = NULL,
                    worker_token = NULL,
                    lease_expires_at = NULL
                WHERE id = ?
                  AND campaign_id = ?
                  AND status = 'RUNNING'
                  AND worker_token = ?
                  AND lease_expires_at > CURRENT_TIMESTAMP(6)
                  AND processed_rows = ?
                  AND processed_rows =
                      added_rows + duplicate_rows + invalid_rows
                """;

        int updated = jdbcTemplate.update(
                sql,
                importId,
                campaignId,
                workerToken,
                expectedProcessedRows
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Import completion rejected: ownership, lease, "
                            + "status or progress check failed for import "
                            + importId
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailedWithLease(
            long importId,
            long campaignId,
            String workerToken,
            String errorMessage) {

        if (workerToken == null || workerToken.isBlank()) {
            throw new IllegalArgumentException(
                    "Worker token must not be blank"
            );
        }

        String message = errorMessage;

        if (message == null || message.isBlank()) {
            message = "Import processing failed";
        }

        if (message.codePointCount(0, message.length()) > 1000) {
            int endIndex = message.offsetByCodePoints(0, 1000);
            message = message.substring(0, endIndex);
        }

        String sql = """
                UPDATE recipient_import
                SET status = 'FAILED',
                    completed_at = CURRENT_TIMESTAMP(6),
                    last_error = ?,
                    worker_token = NULL,
                    lease_expires_at = NULL
                WHERE id = ?
                  AND campaign_id = ?
                  AND status = 'RUNNING'
                  AND worker_token = ?
                  AND lease_expires_at > CURRENT_TIMESTAMP(6)
                """;

        int updated = jdbcTemplate.update(
                sql,
                message,
                importId,
                campaignId,
                workerToken
        );

        if (updated == 0) {
            return false;
        }

        if (updated != 1) {
            throw new IllegalStateException(
                    "Unexpected import failure update result"
            );
        }

        return true;
    }

    public List<RecipientImportStatusResponse> findHistory(
            long campaignId,
            long beforeId,
            int fetchLimit) {

        if (beforeId < 0) {
            throw new IllegalArgumentException(
                    "Import cursor must not be negative"
            );
        }

        if (fetchLimit < 1 || fetchLimit > 101) {
            throw new IllegalArgumentException(
                    "Fetch limit must be between 1 and 101"
            );
        }

        String sql = """
            SELECT
                id,
                campaign_id,
                original_filename,
                status,
                processed_rows,
                added_rows,
                duplicate_rows,
                invalid_rows,
                last_error
            FROM recipient_import
            WHERE campaign_id = ?
            """
                + (beforeId == 0 ? "" : " AND id < ? ")
                + " ORDER BY id DESC LIMIT ?";

        Object[] parameters = beforeId == 0
                ? new Object[]{campaignId, fetchLimit}
                : new Object[]{campaignId, beforeId, fetchLimit};

        return jdbcTemplate.query(
                sql,
                (rs, rowNumber) -> new RecipientImportStatusResponse(
                        rs.getLong("id"),
                        rs.getLong("campaign_id"),
                        rs.getString("original_filename"),
                        rs.getString("status"),
                        rs.getLong("processed_rows"),
                        rs.getLong("added_rows"),
                        rs.getLong("duplicate_rows"),
                        rs.getLong("invalid_rows"),
                        rs.getString("last_error")
                ),
                parameters
        );
    }
}