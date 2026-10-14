package com.email.bulk.validation;

import com.email.bulk.importing.EmailValidationResult;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class EmailAddressPolicy {

    private final Validator validator;

    public EmailAddressPolicy(Validator validator) {
        this.validator = validator;
    }

    public EmailValidationResult validateAndNormalize(String rawEmail) {

        if (rawEmail == null || rawEmail.isBlank()) {
            return invalid("Email must not be blank");
        }

        String email = rawEmail.strip();

        if (email.length() > 254) {
            return invalid("Email must not exceed 254 characters");
        }

        if (email.chars().anyMatch(Character::isISOControl)) {
            return invalid("Email must not contain control characters");
        }

        if (!validator.validate(new EmailValue(email)).isEmpty()) {
            return invalid("Email format is invalid");
        }

        int atIndex = email.lastIndexOf('@');

        if (atIndex <= 0 || atIndex == email.length() - 1) {
            return invalid("Email format is invalid");
        }

        String localPart = email.substring(0, atIndex);

        String domain = email.substring(atIndex + 1)
                .toLowerCase(Locale.ROOT);

        return new EmailValidationResult(
                localPart + "@" + domain,
                null
        );
    }

    private EmailValidationResult invalid(String message) {
        return new EmailValidationResult(null, message);
    }

    private record EmailValue(
            @Email String value
    ) {
    }
}