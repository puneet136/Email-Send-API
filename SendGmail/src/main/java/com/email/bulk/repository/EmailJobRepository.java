package com.email.bulk.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class EmailJobRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmailJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean insertIfAbsent(
            long campaignId,
            String recipientEmail) {

        String sql = """
                INSERT INTO email_job (
                    campaign_id,
                    recipient_email,
                    status,
                    attempt_count
                )
                VALUES (?, ?, 'PENDING', 0)
                """;

        try {
            int rowsInserted = jdbcTemplate.update(
                    sql,
                    campaignId,
                    recipientEmail
            );

            if (rowsInserted != 1) {
                throw new IllegalStateException(
                        "Email job insertion did not insert one row"
                );
            }

            return true;

        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public int insertBatchIfAbsent(
            long campaignId,
            List<String> normalizedEmails) {

        if (normalizedEmails == null) {
            throw new IllegalArgumentException(
                    "Email batch must not be null"
            );
        }

        if (normalizedEmails.isEmpty()) {
            return 0;
        }

        if (normalizedEmails.size() > 500) {
            throw new IllegalArgumentException(
                    "Email batch must not exceed 500 entries"
            );
        }

        String placeholders = String.join(
                ", ",
                Collections.nCopies(
                        normalizedEmails.size(),
                        "(?, ?, 'PENDING', 0)"
                )
        );

        String sql = """
            INSERT INTO email_job (
                campaign_id,
                recipient_email,
                status,
                attempt_count
            )
            VALUES
            """ + placeholders + """
            ON DUPLICATE KEY UPDATE id = id
            """;

        List<Object> parameters =
                new ArrayList<>(normalizedEmails.size() * 2);

        for (String email : normalizedEmails) {
            parameters.add(campaignId);
            parameters.add(email);
        }

        int added = jdbcTemplate.update(
                sql,
                parameters.toArray()
        );

        if (added < 0 || added > normalizedEmails.size()) {
            throw new IllegalStateException(
                    "Unexpected affected-row count for email batch"
            );
        }

        return added;
    }

}