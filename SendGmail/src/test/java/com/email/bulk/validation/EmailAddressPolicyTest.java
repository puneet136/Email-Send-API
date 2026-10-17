package com.email.bulk.validation;

import com.email.bulk.importing.EmailValidationResult;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class EmailAddressPolicyTest {

    private static ValidatorFactory validatorFactory;
    private static EmailAddressPolicy policy;

    @BeforeAll
    static void setUp() {

        validatorFactory = Validation.buildDefaultValidatorFactory();

        policy = new EmailAddressPolicy(
                validatorFactory.getValidator()
        );
    }

    @AfterAll
    static void tearDown() {

        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    void removesSurroundingSpacesAndLowercasesDomain() {

        EmailValidationResult result =
                policy.validateAndNormalize("  Puneet@EXAMPLE.COM  ");

        assertTrue(result.isValid());
        assertNull(result.error());
        assertEquals("Puneet@example.com", result.normalizedEmail());
    }

    @Test
    void normalizesDomainVariantsToTheSameAddress() {

        EmailValidationResult first =
                policy.validateAndNormalize("puneet@EXAMPLE.COM");

        EmailValidationResult second =
                policy.validateAndNormalize("puneet@example.com");

        assertTrue(first.isValid());
        assertTrue(second.isValid());

        assertEquals(
                first.normalizedEmail(),
                second.normalizedEmail()
        );
    }

    @Test
    void preservesLocalPartCase() {

        EmailValidationResult upper =
                policy.validateAndNormalize("Puneet@example.com");

        EmailValidationResult lower =
                policy.validateAndNormalize("puneet@example.com");

        assertTrue(upper.isValid());
        assertTrue(lower.isValid());

        assertEquals("Puneet@example.com", upper.normalizedEmail());
        assertEquals("puneet@example.com", lower.normalizedEmail());
        assertNotEquals(upper.normalizedEmail(), lower.normalizedEmail());
    }

    @Test
    void preservesPlusAddressing() {

        EmailValidationResult result =
                policy.validateAndNormalize(
                        "puneet+updates@EXAMPLE.COM"
                );

        assertTrue(result.isValid());
        assertEquals(
                "puneet+updates@example.com",
                result.normalizedEmail()
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void rejectsBlankInput(String input) {

        assertInvalid(input, "Email must not be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-an-email",
            "@example.com",
            "puneet@",
            "puneet example@example.com"
    })
    void rejectsMalformedAddresses(String input) {

        assertInvalid(input, "Email format is invalid");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "pu\nneet@example.com",
            "pu\rneet@example.com",
            "pu\tneet@example.com"
    })
    void rejectsEmbeddedControlCharacters(String input) {

        assertInvalid(
                input,
                "Email must not contain control characters"
        );
    }

    @Test
    void acceptsValidAddressAt254CharacterBoundary() {

        String email = addressWithLastDomainLabelLength(61);

        assertEquals(254, email.length());

        EmailValidationResult result =
                policy.validateAndNormalize(email);

        assertTrue(result.isValid(), result.error());
        assertEquals(email, result.normalizedEmail());
    }

    @Test
    void rejectsAddressOver254Characters() {

        String email = addressWithLastDomainLabelLength(62);

        assertEquals(255, email.length());

        assertInvalid(
                email,
                "Email must not exceed 254 characters"
        );
    }

    private static String addressWithLastDomainLabelLength(
            int lastLabelLength) {

        return "a".repeat(64)
                + "@"
                + "b".repeat(63)
                + "."
                + "c".repeat(63)
                + "."
                + "d".repeat(lastLabelLength);
    }

    private void assertInvalid(String input, String expectedError) {

        EmailValidationResult result =
                policy.validateAndNormalize(input);

        assertFalse(result.isValid());
        assertNull(result.normalizedEmail());
        assertEquals(expectedError, result.error());
    }
}