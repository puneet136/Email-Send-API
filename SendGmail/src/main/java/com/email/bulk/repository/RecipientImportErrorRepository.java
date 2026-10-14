package com.email.bulk.repository;

import com.email.bulk.importing.ImportRowError;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.email.bulk.api.dto.RecipientImportErrorResponse;
import java.util.*;

@Repository
public class RecipientImportErrorRepository {

    private final JdbcTemplate jdbcTemplate;

    public RecipientImportErrorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertBatch(long importId, List<ImportRowError> errors) {

        if (errors.isEmpty()) {
            return;
        }

        if (errors.size() > 500) {
            throw new IllegalArgumentException(
                    "Error batch must not exceed 500 records"
            );
        }

        String placeholders = String.join(
                ", ",
                Collections.nCopies(errors.size(), "(?, ?, ?, ?)")
        );

        String sql = """
                INSERT INTO recipient_import_error (
                    import_id,
                    record_number,
                    raw_value,
                    reason
                )
                VALUES
                """ + placeholders;

        List<Object> parameters =
                new ArrayList<>(errors.size() * 4);

        for (ImportRowError error : errors) {
            parameters.add(importId);
            parameters.add(error.recordNumber());
            parameters.add(preview(error.rawValue()));
            parameters.add(error.reason());
        }

        int inserted = jdbcTemplate.update(
                sql,
                parameters.toArray()
        );

        if (inserted != errors.size()) {
            throw new IllegalStateException(
                    "Not all import errors were saved"
            );
        }
    }

    private String preview(String value) {

        if (value == null) {
            return null;
        }

        if (value.codePointCount(0, value.length()) <= 512) {
            return value;
        }

        int endIndex = value.offsetByCodePoints(0, 512);
        return value.substring(0, endIndex);
    }

    public List<RecipientImportErrorResponse> findAfterRecord(
            long campaignId,
            long importId,
            long afterRecord,
            int fetchLimit) {

        if (afterRecord < 0 || fetchLimit < 1 || fetchLimit > 101) {
            throw new IllegalArgumentException(
                    "Invalid error query bounds"
            );
        }

        String sql = """
            SELECT
                e.record_number,
                e.raw_value,
                e.reason
            FROM recipient_import_error e
            JOIN recipient_import i ON i.id = e.import_id
            WHERE i.campaign_id = ?
              AND e.import_id = ?
              AND e.record_number > ?
            ORDER BY e.record_number
            LIMIT ?
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNumber) ->
                        new RecipientImportErrorResponse(
                                rs.getLong("record_number"),
                                rs.getString("raw_value"),
                                rs.getString("reason")
                        ),
                campaignId,
                importId,
                afterRecord,
                fetchLimit
        );
    }

}