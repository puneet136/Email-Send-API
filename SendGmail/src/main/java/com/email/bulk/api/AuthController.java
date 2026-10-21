package com.email.bulk.api;

import com.email.bulk.api.dto.*;
import com.email.bulk.auth.UserAccount;
import com.email.bulk.exception.InvalidCredentialsException;
import com.email.bulk.repository.UserAccountRepository;
import com.email.bulk.service.JwtTokenService;
import com.email.bulk.service.UserLoginService;
import com.email.bulk.service.UserRegistrationService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRegistrationService registrationService;
    private final UserLoginService loginService;
    private final JwtTokenService tokenService;
    private final UserAccountRepository userRepository;

    public AuthController(
            UserRegistrationService registrationService,
            UserLoginService loginService,
            JwtTokenService tokenService,
            UserAccountRepository userRepository) {

        this.registrationService = registrationService;
        this.loginService = loginService;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @RequestBody RegisterRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(registrationService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request) {

        UserResponse user = loginService.authenticate(request);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(tokenService.issue(user));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            @AuthenticationPrincipal Jwt jwt) {

        long userId = Long.parseLong(jwt.getSubject());

        UserAccount user = userRepository.findById(userId)
                .filter(UserAccount::enabled)
                .orElseThrow(InvalidCredentialsException::new);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new UserResponse(
                        user.id(),
                        user.displayName(),
                        user.email()
                ));
    }
}