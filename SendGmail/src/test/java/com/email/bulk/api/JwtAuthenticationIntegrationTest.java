package com.email.bulk.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        locations = "classpath:application-test.properties"
)
@Transactional
class JwtAuthenticationIntegrationTest {

    private static final String PASSWORD =
            "Test-only JWT passphrase 2026!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Value("${app.security.jwt.issuer}")
    private String issuer;

    @Value("${app.security.jwt.audience}")
    private String audience;

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
    void loginReturnsUsableJwtWithoutCreatingSession()
            throws Exception {

        TestAccount account = register();

        MvcResult loginResult = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                        "email", account.email(),
                                        "password", PASSWORD
                                )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email")
                        .value(account.email()))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andReturn();

        assertNull(loginResult.getRequest().getSession(false));
        assertNull(loginResult.getResponse().getCookie("JSESSIONID"));

        String token = objectMapper.readTree(
                loginResult.getResponse().getContentAsString()
        ).get("accessToken").asText();

        Jwt decoded = jwtDecoder.decode(token);

        assertEquals(
                Long.toString(account.id()),
                decoded.getSubject()
        );
        assertEquals(List.of(audience), decoded.getAudience());
        assertNotNull(decoded.getExpiresAt());
        assertNotNull(decoded.getIssuedAt());
        assertEquals(
                900L,
                decoded.getExpiresAt().getEpochSecond()
                        - decoded.getIssuedAt().getEpochSecond()
        );

        MvcResult meResult = mockMvc.perform(
                        get("/api/v1/auth/me")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(account.email()))
                .andExpect(jsonPath("$.displayName").value("JWT Test"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        assertNull(meResult.getRequest().getSession(false));

        // A previous successful request does not authenticate
        // a later request that omits its bearer token.
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongPasswordWithoutIssuingToken() throws Exception {

        TestAccount account = register();

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                        "email", account.email(),
                                        "password", "Incorrect long password"
                                )))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void rejectsMissingBearerToken() throws Exception {

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredToken() throws Exception {

        TestAccount account = register();

        // Expired well beyond the decoder's clock-skew allowance.
        String token = signedToken(
                account.id(),
                issuer,
                audience,
                Instant.now().minusSeconds(600),
                Instant.now().minusSeconds(300)
        );

        assertRejected(token);
    }

    @Test
    void rejectsModifiedPayloadWithOriginalSignature()
            throws Exception {

        TestAccount account = register();
        String original = login(account.email());

        String[] parts = original.split("\\.");
        assertEquals(3, parts.length);

        String payload = new String(
                Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8
        );

        var modifiedPayload = (com.fasterxml.jackson.databind.node.ObjectNode)
                objectMapper.readTree(payload);

        modifiedPayload.put(
                "sub",
                Long.toString(account.id() + 1)
        );

        String modifiedPayloadBase64 = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        objectMapper.writeValueAsBytes(modifiedPayload)
                );

        String modifiedToken = parts[0]
                + "." + modifiedPayloadBase64
                + "." + parts[2];

        assertRejected(modifiedToken);
    }

    @Test
    void rejectsWrongAudienceEvenWithValidSignature()
            throws Exception {

        TestAccount account = register();

        String token = signedToken(
                account.id(),
                issuer,
                "another-application",
                Instant.now(),
                Instant.now().plusSeconds(900)
        );

        assertRejected(token);
    }

    @Test
    void rejectsWrongIssuerEvenWithValidSignature()
            throws Exception {

        TestAccount account = register();

        String token = signedToken(
                account.id(),
                "another-issuer",
                audience,
                Instant.now(),
                Instant.now().plusSeconds(900)
        );

        assertRejected(token);
    }

    @Test
    void disabledAccountCannotUseMeOrLoginAgain()
            throws Exception {

        TestAccount account = register();
        String token = login(account.email());

        assertEquals(
                1,
                jdbcTemplate.update(
                        "UPDATE app_user SET enabled = FALSE WHERE id = ?",
                        account.id()
                )
        );

        // JWT remains cryptographically valid, but /me checks account state.
        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                        "email", account.email(),
                                        "password", PASSWORD
                                )))
                )
                .andExpect(status().isUnauthorized());
    }

    private TestAccount register() throws Exception {

        String email = "jwt-" + UUID.randomUUID() + "@example.com";

        MvcResult result = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                        "displayName", "JWT Test",
                                        "email", email,
                                        "password", PASSWORD
                                )))
                )
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        return new TestAccount(response.get("id").asLong(), email);
    }

    private String login(String email) throws Exception {

        MvcResult result = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                        "email", email,
                                        "password", PASSWORD
                                )))
                )
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(
                result.getResponse().getContentAsString()
        ).get("accessToken").asText();
    }

    private String signedToken(
            long userId,
            String tokenIssuer,
            String tokenAudience,
            Instant issuedAt,
            Instant expiresAt) {

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(tokenIssuer)
                .subject(Long.toString(userId))
                .audience(List.of(tokenAudience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }

    private void assertRejected(String token) throws Exception {

        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isUnauthorized());
    }

    private String json(Map<String, String> values) throws Exception {
        return objectMapper.writeValueAsString(values);
    }

    private record TestAccount(long id, String email) {
    }
}