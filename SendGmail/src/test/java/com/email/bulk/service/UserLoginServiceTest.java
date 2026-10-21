package com.email.bulk.service;

import com.email.bulk.api.dto.LoginRequest;
import com.email.bulk.api.dto.UserResponse;
import com.email.bulk.auth.UserAccount;
import com.email.bulk.exception.InvalidCredentialsException;
import com.email.bulk.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserLoginServiceTest {

    private UserAccountRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserLoginService loginService;

    @BeforeEach
    void setUp() {

        userRepository = mock(UserAccountRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);

        when(passwordEncoder.encode(anyString()))
                .thenReturn("dummy-hash");

        loginService = new UserLoginService(
                userRepository,
                passwordEncoder
        );
    }

    @Test
    void authenticatesAndNormalizesEmailWithoutChangingPassword() {

        UserAccount account = account(true);

        when(userRepository.findByEmail("puneet@example.com"))
                .thenReturn(Optional.of(account));

        String password = "  My long password  ";

        when(passwordEncoder.matches(password, "stored-hash"))
                .thenReturn(true);

        UserResponse response = loginService.authenticate(
                new LoginRequest(
                        " PUNEET@EXAMPLE.COM ",
                        password
                )
        );

        assertEquals(
                new UserResponse(7L, "Puneet", "puneet@example.com"),
                response
        );

        verify(userRepository).findByEmail("puneet@example.com");

        verify(passwordEncoder).matches(
                password,
                "stored-hash"
        );
    }

    @Test
    void rejectsWrongPassword() {

        when(userRepository.findByEmail("puneet@example.com"))
                .thenReturn(Optional.of(account(true)));

        when(passwordEncoder.matches("wrong", "stored-hash"))
                .thenReturn(false);

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> loginService.authenticate(
                        new LoginRequest(
                                "puneet@example.com",
                                "wrong"
                        )
                )
        );

        assertEquals(
                "Email or password is incorrect",
                exception.getMessage()
        );
    }

    @Test
    void missingAccountStillChecksDummyHash() {

        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> loginService.authenticate(
                        new LoginRequest(
                                "missing@example.com",
                                "Some long password"
                        )
                )
        );

        verify(passwordEncoder).matches(
                "Some long password",
                "dummy-hash"
        );
    }

    @Test
    void rejectsDisabledAccountEvenWithCorrectPassword() {

        when(userRepository.findByEmail("puneet@example.com"))
                .thenReturn(Optional.of(account(false)));

        when(passwordEncoder.matches(
                "Correct long password",
                "stored-hash"
        )).thenReturn(true);

        assertThrows(
                InvalidCredentialsException.class,
                () -> loginService.authenticate(
                        new LoginRequest(
                                "puneet@example.com",
                                "Correct long password"
                        )
                )
        );
    }

    @Test
    void rejectsMissingFieldsBeforeDatabaseLookup() {

        assertThrows(
                InvalidCredentialsException.class,
                () -> loginService.authenticate(null)
        );

        assertThrows(
                InvalidCredentialsException.class,
                () -> loginService.authenticate(
                        new LoginRequest(null, "Some password")
                )
        );

        assertThrows(
                InvalidCredentialsException.class,
                () -> loginService.authenticate(
                        new LoginRequest("puneet@example.com", null)
                )
        );

        verifyNoInteractions(userRepository);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void rejectsOversizedInputBeforeDatabaseLookup() {

        assertThrows(
                InvalidCredentialsException.class,
                () -> loginService.authenticate(
                        new LoginRequest(
                                "x".repeat(255),
                                "Some password"
                        )
                )
        );

        assertThrows(
                InvalidCredentialsException.class,
                () -> loginService.authenticate(
                        new LoginRequest(
                                "puneet@example.com",
                                "x".repeat(129)
                        )
                )
        );

        verifyNoInteractions(userRepository);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    private UserAccount account(boolean enabled) {
        return new UserAccount(
                7L,
                "Puneet",
                "puneet@example.com",
                "stored-hash",
                enabled
        );
    }
}