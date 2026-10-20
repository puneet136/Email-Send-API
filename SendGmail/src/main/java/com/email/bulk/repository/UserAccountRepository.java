package com.email.bulk.repository;

import com.email.bulk.auth.UserAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;

@Repository
public class UserAccountRepository {

    private static final RowMapper<UserAccount> USER_MAPPER =
            (rs, rowNumber) -> new UserAccount(
                    rs.getLong("id"),
                    rs.getString("display_name"),
                    rs.getString("email"),
                    rs.getString("password_hash"),
                    rs.getBoolean("enabled")
            );

    private final JdbcTemplate jdbcTemplate;

    public UserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(
            String displayName,
            String normalizedEmail,
            String passwordHash) {

        String sql = """
                INSERT INTO app_user (
                    display_name,
                    email,
                    password_hash,
                    enabled
                )
                VALUES (?, ?, ?, TRUE)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        int inserted = jdbcTemplate.update(connection -> {

            PreparedStatement statement = connection.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            );

            statement.setString(1, displayName);
            statement.setString(2, normalizedEmail);
            statement.setString(3, passwordHash);

            return statement;

        }, keyHolder);

        Number generatedId = keyHolder.getKey();

        if (inserted != 1 || generatedId == null) {
            throw new IllegalStateException(
                    "User creation did not return an ID"
            );
        }

        return generatedId.longValue();
    }

    public Optional<UserAccount> findByEmail(String normalizedEmail) {

        String sql = """
                SELECT id, display_name, email, password_hash, enabled
                FROM app_user
                WHERE email = ?
                """;

        return jdbcTemplate.query(
                sql,
                USER_MAPPER,
                normalizedEmail
        ).stream().findFirst();
    }

    public Optional<UserAccount> findById(long userId) {

        String sql = """
                SELECT id, display_name, email, password_hash, enabled
                FROM app_user
                WHERE id = ?
                """;

        return jdbcTemplate.query(
                sql,
                USER_MAPPER,
                userId
        ).stream().findFirst();
    }
}