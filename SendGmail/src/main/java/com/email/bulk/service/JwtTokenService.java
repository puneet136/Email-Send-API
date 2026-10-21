package com.email.bulk.service;

import com.email.bulk.api.dto.LoginResponse;
import com.email.bulk.api.dto.UserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final long accessTokenSeconds;

    public JwtTokenService(
            JwtEncoder encoder,
            @Value("${app.security.jwt.issuer}") String issuer,
            @Value("${app.security.jwt.audience}") String audience,
            @Value("${app.security.jwt.access-token-seconds}")
            long accessTokenSeconds) {

        if (accessTokenSeconds < 60 || accessTokenSeconds > 3600) {
            throw new IllegalArgumentException(
                    "Access token duration must be between 60 and 3600 seconds"
            );
        }

        this.encoder = encoder;
        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenSeconds = accessTokenSeconds;
    }

    public LoginResponse issue(UserResponse user) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(Long.toString(user.id()))
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTokenSeconds))
                .id(UUID.randomUUID().toString())
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        String token = encoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();

        return new LoginResponse(
                token,
                "Bearer",
                accessTokenSeconds,
                user
        );
    }
}