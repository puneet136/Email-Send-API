package com.email.bulk.api;

import com.email.bulk.auth.UserAccount;
import com.email.bulk.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        locations = "classpath:application-test.properties"
)
@Transactional
class UserRegistrationIntegrationTest {

    private static final String PASSWORD =
            "Test-only long passphrase 2026!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void verifyTestDatabase() {

        assertEquals(
                "bulkemailtest",
                jdbcTemplate.queryForObject(
                        "SELECT DATABASE()",
                        String.class
                )
        );
    }

    @Test
    void registersNormalizedAccountAndStoresPasswordHash()
            throws Exception {

        String email = uniqueEmail();

        String response = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(
                                        "  Test User  ",
                                        " " + email.toUpperCase(Locale.ROOT) + " ",
                                        PASSWORD
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.displayName").value("Test User"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UserAccount account = userRepository.findByEmail(email)
                .orElseThrow();

        assertEquals(
                account.id(),
                objectMapper.readTree(response).get("id").asLong()
        );
        assertTrue(account.enabled());
        assertNotEquals(PASSWORD, account.passwordHash());
        assertTrue(passwordEncoder.matches(
                PASSWORD,
                account.passwordHash()
        ));
        assertFalse(passwordEncoder.matches(
                "A different test password",
                account.passwordHash()
        ));
    }

    @Test
    void rejectsDuplicateNormalizedEmailWithoutChangingPassword()
            throws Exception {

        String email = uniqueEmail();

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("First User", email, PASSWORD))
                )
                .andExpect(status().isCreated());

        UserAccount original = userRepository.findByEmail(email)
                .orElseThrow();

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(
                                        "Second User",
                                        email.toUpperCase(Locale.ROOT),
                                        "Another long test password!"
                                ))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        UserAccount unchanged = userRepository.findByEmail(email)
                .orElseThrow();

        assertEquals(original.id(), unchanged.id());
        assertEquals(original.displayName(), unchanged.displayName());
        assertEquals(original.passwordHash(), unchanged.passwordHash());

        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM app_user WHERE email = ?",
                        Long.class,
                        email
                ).longValue()
        );
    }

    @Test
    void rejectsShortPasswordWithoutCreatingAccount()
            throws Exception {

        String email = uniqueEmail();

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("Test User", email, "short"))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertTrue(userRepository.findByEmail(email).isEmpty());
    }

    @Test
    void rejectsInvalidEmail() throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(
                                        "Test User",
                                        "not-an-email",
                                        PASSWORD
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsMissingPassword() throws Exception {

        String email = uniqueEmail();

        String request = objectMapper.writeValueAsString(
                Map.of(
                        "displayName", "Test User",
                        "email", email
                )
        );

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest());

        assertTrue(userRepository.findByEmail(email).isEmpty());
    }

    @Test
    void samePasswordProducesDifferentHashesForDifferentUsers()
            throws Exception {

        String firstEmail = uniqueEmail();
        String secondEmail = uniqueEmail();

        for (String email : new String[]{firstEmail, secondEmail}) {

            mockMvc.perform(
                            post("/api/v1/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body("Test User", email, PASSWORD))
                    )
                    .andExpect(status().isCreated());
        }

        String firstHash = userRepository.findByEmail(firstEmail)
                .orElseThrow()
                .passwordHash();

        String secondHash = userRepository.findByEmail(secondEmail)
                .orElseThrow()
                .passwordHash();

        assertNotEquals(firstHash, secondHash);
        assertTrue(passwordEncoder.matches(PASSWORD, firstHash));
        assertTrue(passwordEncoder.matches(PASSWORD, secondHash));
    }

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }

    private String body(
            String displayName,
            String email,
            String password) throws Exception {

        // Use a map because RegisterRequest.password is write-only
        // and intentionally excluded during JSON serialization.
        return objectMapper.writeValueAsString(
                Map.of(
                        "displayName", displayName,
                        "email", email,
                        "password", password
                )
        );
    }
}