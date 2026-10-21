package com.email.bulk.service;

import com.email.bulk.exception.InvalidCredentialsException;
import com.email.bulk.repository.UserAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserAccountRepository userRepository;

    public CurrentUserService(UserAccountRepository userRepository) {
        this.userRepository = userRepository;
    }

    public long requireUserId() {

        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !jwt.isAuthenticated()) {
            throw new InvalidCredentialsException();
        }

        long userId;

        try {
            userId = Long.parseLong(jwt.getToken().getSubject());
        } catch (NumberFormatException exception) {
            throw new InvalidCredentialsException();
        }

        if (userId <= 0) {
            throw new InvalidCredentialsException();
        }

        return userRepository.findById(userId)
                .filter(user -> user.enabled())
                .orElseThrow(InvalidCredentialsException::new)
                .id();
    }
}