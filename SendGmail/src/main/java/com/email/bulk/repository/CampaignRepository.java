package com.email.bulk.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import com.email.bulk.api.dto.CampaignDetailResponse;
import java.util.Optional;
import java.sql.PreparedStatement;
import java.sql.Statement;

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
}