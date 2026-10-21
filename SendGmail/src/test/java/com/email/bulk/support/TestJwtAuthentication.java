package com.email.bulk.support;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

public final class TestJwtAuthentication {

    private TestJwtAuthentication() {
    }

    public static void authenticate(long userId) {

        Instant now = Instant.now();

        Jwt jwt = Jwt.withTokenValue("test-only-token")
                .header("alg", "HS256")
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .build();

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(
                new JwtAuthenticationToken(jwt, List.of())
        );

        SecurityContextHolder.setContext(context);
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}