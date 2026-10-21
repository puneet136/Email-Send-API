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
import com.email.bulk.api.dto.CampaignListItemResponse;
import java.util.List;

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

    public List<CampaignListItemResponse> findPage(
            long beforeId,
            int fetchLimit) {

        if (beforeId < 0) {
            throw new IllegalArgumentException(
                    "Campaign cursor must not be negative"
            );
        }

        if (fetchLimit < 1 || fetchLimit > 101) {
            throw new IllegalArgumentException(
                    "Fetch limit must be between 1 and 101"
            );
        }

        String sql = """
            SELECT id, name, subject, status
            FROM campaign
            """
                + (beforeId == 0 ? "" : " WHERE id < ? ")
                + " ORDER BY id DESC LIMIT ?";

        Object[] parameters = beforeId == 0
                ? new Object[]{fetchLimit}
                : new Object[]{beforeId, fetchLimit};

        return jdbcTemplate.query(
                sql,
                (rs, rowNumber) -> new CampaignListItemResponse(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("subject"),
                        rs.getString("status")
                ),
                parameters
        );
    }

    public long insertOwned(
            long ownerUserId,
            String name,
            String subject,
            String message) {

        if (ownerUserId <= 0) {
            throw new IllegalArgumentException(
                    "Owner user ID must be positive"
            );
        }

        String sql = """
            INSERT INTO campaign (
                owner_user_id,
                name,
                subject,
                body,
                status
            )
            VALUES (?, ?, ?, ?, 'DRAFT')
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        int inserted = jdbcTemplate.update(connection -> {

            PreparedStatement statement = connection.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            );

            statement.setLong(1, ownerUserId);
            statement.setString(2, name);
            statement.setString(3, subject);
            statement.setString(4, message);

            return statement;

        }, keyHolder);

        Number generatedId = keyHolder.getKey();

        if (inserted != 1 || generatedId == null) {
            throw new IllegalStateException(
                    "Campaign creation did not return an ID"
            );
        }

        return generatedId.longValue();
    }

    public Optional<CampaignDetailResponse> findOwnedById(
            long campaignId,
            long ownerUserId) {

        String sql = """
            SELECT id, name, subject, body, status
            FROM campaign
            WHERE id = ?
              AND owner_user_id = ?
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNumber) -> new CampaignDetailResponse(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("subject"),
                        rs.getString("body"),
                        rs.getString("status")
                ),
                campaignId,
                ownerUserId
        ).stream().findFirst();
    }

    public List<CampaignListItemResponse> findOwnedPage(
            long ownerUserId,
            long beforeId,
            int fetchLimit) {

        if (ownerUserId <= 0) {
            throw new IllegalArgumentException(
                    "Owner user ID must be positive"
            );
        }

        if (beforeId < 0) {
            throw new IllegalArgumentException(
                    "Campaign cursor must not be negative"
            );
        }

        if (fetchLimit < 1 || fetchLimit > 101) {
            throw new IllegalArgumentException(
                    "Fetch limit must be between 1 and 101"
            );
        }

        String sql = """
            SELECT id, name, subject, status
            FROM campaign
            WHERE owner_user_id = ?
            """
                + (beforeId == 0 ? "" : " AND id < ? ")
                + " ORDER BY id DESC LIMIT ?";

        Object[] parameters = beforeId == 0
                ? new Object[]{ownerUserId, fetchLimit}
                : new Object[]{ownerUserId, beforeId, fetchLimit};

        return jdbcTemplate.query(
                sql,
                (rs, rowNumber) -> new CampaignListItemResponse(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("subject"),
                        rs.getString("status")
                ),
                parameters
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<String> lockOwnedStatus(
            long campaignId,
            long ownerUserId) {

        String sql = """
        SELECT status
        FROM campaign
        WHERE id = ?
          AND owner_user_id = ?
        FOR UPDATE
        """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNumber) -> rs.getString("status"),
                campaignId,
                ownerUserId
        ).stream().findFirst();
    }

}