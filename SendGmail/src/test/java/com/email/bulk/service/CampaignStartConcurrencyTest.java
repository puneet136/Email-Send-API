package com.email.bulk.service;

import com.email.bulk.exception.CampaignConflictException;
import com.email.bulk.repository.CampaignRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import com.email.bulk.repository.UserAccountRepository;
import com.email.bulk.support.TestJwtAuthentication;
import java.util.UUID;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        locations = "classpath:application-test.properties"
)
class CampaignStartConcurrencyTest {

    @Autowired
    private CampaignStartService campaignStartService;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long campaignId;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private Long ownerUserId;

    @BeforeEach
    void setUp() {

        assertEquals(
                "bulkemailtest",
                jdbcTemplate.queryForObject(
                        "SELECT DATABASE()",
                        String.class
                )
        );

        ownerUserId = userAccountRepository.insert(
                "Concurrent start owner",
                "concurrent-" + UUID.randomUUID() + "@example.com",
                "unused-test-password-hash"
        );

        campaignId = campaignRepository.insertOwned(
                ownerUserId,
                "Concurrent start test",
                "Test subject",
                "Test body"
        );

        jdbcTemplate.update(
                """
                INSERT INTO email_job (
                    campaign_id,
                    recipient_email,
                    status,
                    attempt_count
                )
                VALUES (?, 'concurrency-test@example.com', 'PENDING', 0)
                """,
                campaignId
        );
    }

    @AfterEach
    void cleanUp() {

        TestJwtAuthentication.clear();

        if (campaignId != null) {
            jdbcTemplate.update(
                    "DELETE FROM email_job WHERE campaign_id = ?",
                    campaignId
            );

            jdbcTemplate.update(
                    "DELETE FROM campaign WHERE id = ?",
                    campaignId
            );
        }

        if (ownerUserId != null) {
            jdbcTemplate.update(
                    "DELETE FROM app_user WHERE id = ?",
                    ownerUserId
            );
        }
    }

    @Test
    void onlyOneConcurrentStartSucceeds() throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<String> request = () -> {

                try {
                    TestJwtAuthentication.authenticate(ownerUserId);

                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting for start signal"
                        );
                    }

                    try {
                        campaignStartService.start(campaignId);
                        return "STARTED";

                    } catch (CampaignConflictException exception) {
                        return "CONFLICT";
                    }

                } finally {
                    TestJwtAuthentication.clear();
                }
            };

            Future<String> first = executor.submit(request);
            Future<String> second = executor.submit(request);

            assertTrue(
                    ready.await(5, TimeUnit.SECONDS),
                    "Both request threads must become ready"
            );

            start.countDown();

            List<String> results = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );

            assertEquals(
                    1L,
                    results.stream()
                            .filter("STARTED"::equals)
                            .count()
            );

            assertEquals(
                    1L,
                    results.stream()
                            .filter("CONFLICT"::equals)
                            .count()
            );

            assertEquals(
                    "RUNNING",
                    jdbcTemplate.queryForObject(
                            "SELECT status FROM campaign WHERE id = ?",
                            String.class,
                            campaignId
                    )
            );

            assertEquals(
                    1,
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM campaign
                            WHERE id = ?
                              AND started_at IS NOT NULL
                              AND completed_at IS NULL
                            """,
                            Integer.class,
                            campaignId
                    ).intValue()
            );

        } finally {
            start.countDown();

            executor.shutdown();

            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                executor.shutdownNow();

                assertTrue(
                        executor.awaitTermination(10, TimeUnit.SECONDS),
                        "Concurrent request threads did not stop"
                );
            }
        }
    }
}