package com.email.bulk.service;

import com.email.bulk.api.dto.RegisterRequest;
import com.email.bulk.api.dto.UserResponse;
import com.email.bulk.exception.InvalidRegistrationException;
import com.email.bulk.exception.RegistrationConflictException;
import com.email.bulk.repository.UserAccountRepository;
import jakarta.validation.Validator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class UserRegistrationService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    public UserRegistrationService(
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder,
            Validator validator) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    public UserResponse register(RegisterRequest request) {

        if (request == null) {
            throw new InvalidRegistrationException(
                    "Registration details are required"
            );
        }

        // Check raw lengths before normalization or expensive hashing.
        // Bound raw input before normalization or expensive hashing.
        if (request.displayName() == null
                || request.email() == null
                || request.password() == null
                || request.displayName().length() > 100
                || request.email().length() > 254
                || request.password().length() > 128) {

            throw new InvalidRegistrationException(
                    "Provide a name of up to 100 characters, "
                            + "a valid email of up to 254 characters, "
                            + "and a password of 15 to 128 characters"
            );
        }

        if (containsControlCharacters(request.displayName())
                || containsControlCharacters(request.email())) {

            throw new InvalidRegistrationException(
                    "Name and email must not contain control characters"
            );
        }

        String displayName = request.displayName().strip();
        String email = request.email()
                .strip()
                .toLowerCase(Locale.ROOT);

        RegisterRequest normalized = new RegisterRequest(
                displayName,
                email,
                request.password()
        );

        if (!validator.validate(normalized).isEmpty()) {
            throw new InvalidRegistrationException(
                    "Registration details are invalid"
            );
        }

        String passwordHash = passwordEncoder.encode(
                request.password()
        );

        long userId;

        try {
            userId = userRepository.insert(
                    displayName,
                    email,
                    passwordHash
            );

        } catch (DuplicateKeyException exception) {
            throw new RegistrationConflictException();
        }

        return new UserResponse(userId, displayName, email);
    }

    private boolean containsControlCharacters(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}