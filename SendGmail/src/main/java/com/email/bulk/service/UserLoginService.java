package com.email.bulk.service;

import com.email.bulk.api.dto.LoginRequest;
import com.email.bulk.api.dto.UserResponse;
import com.email.bulk.auth.UserAccount;
import com.email.bulk.exception.InvalidCredentialsException;
import com.email.bulk.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class UserLoginService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public UserLoginService(
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;

        // Missing accounts still perform password hashing work.
        // This reduces a simple timing difference between
        // "account missing" and "wrong password".
        this.dummyPasswordHash = passwordEncoder.encode(
                UUID.randomUUID().toString()
        );
    }

    public UserResponse authenticate(LoginRequest request) {

        if (request == null
                || request.email() == null
                || request.password() == null
                || request.email().isBlank()
                || request.email().length() > 254
                || request.password().isEmpty()
                || request.password().length() > 128
                || request.email().codePoints()
                .anyMatch(Character::isISOControl)) {

            throw new InvalidCredentialsException();
        }

        String email = request.email()
                .strip()
                .toLowerCase(Locale.ROOT);

        UserAccount account = userRepository.findByEmail(email)
                .orElse(null);

        String storedHash = account == null
                ? dummyPasswordHash
                : account.passwordHash();

        // Password is never trimmed or converted to lowercase.
        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                storedHash
        );

        if (account == null
                || !passwordMatches
                || !account.enabled()) {

            throw new InvalidCredentialsException();
        }

        return new UserResponse(
                account.id(),
                account.displayName(),
                account.email()
        );
    }
}