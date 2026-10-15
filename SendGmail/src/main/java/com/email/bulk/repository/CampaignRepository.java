package com.email.bulk.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import com.email.bulk.api.dto.CampaignDetailResponse;
import java.util.Optional;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.email.bulk.api.dto.CampaignProgressResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Repository
public class CampaignRepository {

    private final JdbcTemplate jdbcTemplate;

    public CampaignRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(String name, String subject, String message) {

        String sql = """
                INSERT INTO campaign (name, subject, body, status)
                VALUES (?, ?, ?, 'DRAFT')
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        int rowsInserted = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            );

            statement.setString(1, name);
            statement.setString(2, subject);
            statement.setString(3, message);

            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();

        if (rowsInserted != 1 || generatedId == null) {
            throw new IllegalStateException(
                    "Campaign creation did not return an ID"
            );
        }

        return generatedId.longValue();
    }

    public Optional<CampaignDetailResponse> findById(long id) {

        String sql = """
            SELECT id, name, subject, body, status
            FROM campaign
            WHERE id = ?
            """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new CampaignDetailResponse(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getString("subject"),
                        resultSet.getString("body"),
                        resultSet.getString("status")
                ),
                id
        ).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<String> lockStatus(long campaignId) {

        String sql = """
            SELECT status
            FROM campaign
            WHERE id = ?
            FOR UPDATE
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNumber) -> rs.getString("status"),
                campaignId
        ).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean hasActiveImports(long campaignId) {

        String sql = """
            SELECT id
            FROM recipient_import
            WHERE campaign_id = ?
              AND status IN ('QUEUED', 'RUNNING')
            LIMIT 1
            FOR UPDATE
            """;

        return !jdbcTemplate.query(
                sql,
                (rs, rowNumber) -> rs.getLong("id"),
                campaignId
        ).isEmpty();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean hasPendingRecipients(long campaignId) {

        String sql = """
            SELECT id
            FROM email_job
            WHERE campaign_id = ?
              AND status = 'PENDING'
            LIMIT 1
            FOR UPDATE
            """;

        return !jdbcTemplate.query(
                sql,
                (rs, rowNumber) -> rs.getLong("id"),
                campaignId
        ).isEmpty();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markRunning(long campaignId) {

        String sql = """
            UPDATE campaign
            SET status = 'RUNNING',
                started_at = CURRENT_TIMESTAMP(6),
                completed_at = NULL
            WHERE id = ?
              AND status = 'DRAFT'
            """;

        int updated = jdbcTemplate.update(sql, campaignId);

        if (updated != 1) {
            throw new IllegalStateException(
                    "Campaign could not be marked running"
            );
        }
    }

    public Optional<CampaignProgressResponse> findProgress(long campaignId) {

        String sql = """
            SELECT
                c.id AS campaign_id,
                c.status AS campaign_status,
                COUNT(j.id) AS total_recipients,
                COALESCE(SUM(
                    CASE WHEN j.status = 'PENDING' THEN 1 ELSE 0 END
                ), 0) AS pending_count,
                COALESCE(SUM(
                    CASE WHEN j.status = 'SENDING' THEN 1 ELSE 0 END
                ), 0) AS sending_count,
                COALESCE(SUM(
                    CASE WHEN j.status = 'RETRY' THEN 1 ELSE 0 END
                ), 0) AS retry_count,
                COALESCE(SUM(
                    CASE WHEN j.status = 'SENT' THEN 1 ELSE 0 END
                ), 0) AS sent_count,
                COALESCE(SUM(
                    CASE WHEN j.status = 'FAILED' THEN 1 ELSE 0 END
                ), 0) AS failed_count
            FROM campaign c
            LEFT JOIN email_job j ON j.campaign_id = c.id
            WHERE c.id = ?
            GROUP BY c.id, c.status
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNumber) -> {

                    long total = rs.getLong("total_recipients");
                    long pending = rs.getLong("pending_count");
                    long sending = rs.getLong("sending_count");
                    long retry = rs.getLong("retry_count");
                    long sent = rs.getLong("sent_count");
                    long failed = rs.getLong("failed_count");

                    long processed = sent + failed;

                    BigDecimal progressPercent = total == 0
                            ? BigDecimal.ZERO.setScale(2)
                            : BigDecimal.valueOf(processed)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(
                                    BigDecimal.valueOf(total),
                                    2,
                                    RoundingMode.HALF_UP
                            );

                    return new CampaignProgressResponse(
                            rs.getLong("campaign_id"),
                            rs.getString("campaign_status"),
                            total,
                            pending,
                            sending,
                            retry,
                            sent,
                            failed,
                            processed,
                            progressPercent
                    );
                },
                campaignId
        ).stream().findFirst();
    }
}