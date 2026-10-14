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
}