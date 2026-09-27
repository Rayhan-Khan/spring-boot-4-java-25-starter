package com.mss.base.service.impl;

import com.mss.base.config.AuthSettings;
import com.mss.base.dto.LoginRequest;
import com.mss.base.dto.RegisterRequest;
import com.mss.base.entity.User;
import com.mss.base.enums.UserRole;
import com.mss.base.enums.UserStatus;
import com.mss.base.exception.AuthMethodDisabledException;
import com.mss.base.exception.CustomMessagePresentException;
import com.mss.base.repository.UserRepository;
import com.mss.base.response.AuthTokens;
import com.mss.base.response.LoginResponse;
import com.mss.base.service.AuthService;
import com.mss.base.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Email/password registration and login.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final String METHOD_NAME = "Email and password login";
    private static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthSettings authSettings;

    /**
     * Compared against when the email is unknown, so a wrong email takes as long as a wrong password
     * and response times do not reveal which emails are registered. Only computed when JWT login is enabled.
     */
    private final String unknownUserHash;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           TokenService tokenService, AuthSettings authSettings) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.authSettings = authSettings;
        this.unknownUserHash = authSettings.jwtEnabled() ? passwordEncoder.encode(UUID.randomUUID().toString()) : null;
    }

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        requireEnabled();
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new CustomMessagePresentException("This email is already registered.");
        }

        User user = new User();
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setIsEmailVerified(false);
        user.setRole(UserRole.USER);
        user.setUserStatus(UserStatus.Active);
        User saved = userRepository.save(user);

        logger.info("Registered user {} with email and password.", saved.getId());
        return LoginResponse.from(saved, tokenService.issueTokens(saved));
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        requireEnabled();
        Optional<User> found = userRepository.findByEmailIgnoreCase(normalizeEmail(request.getEmail()));
        String passwordHash = found.map(User::getPasswordHash).orElse(null);

        if (passwordHash == null) {
            // Unknown email, or a Firebase-only user without a password
            passwordEncoder.matches(request.getPassword(), unknownUserHash);
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.getPassword(), passwordHash)) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        User user = found.get();
        if (user.getUserStatus() == UserStatus.Inactive) {
            throw new DisabledException("Your account is inactive.");
        }

        logger.info("User {} logged in with email and password.", user.getId());
        return LoginResponse.from(user, tokenService.issueTokens(user));
    }

    @Override
    public AuthTokens refresh(String refreshToken) {
        requireEnabled();
        return tokenService.refresh(refreshToken);
    }

    @Override
    public void logout(String refreshToken) {
        requireEnabled();
        tokenService.revoke(refreshToken);
    }

    private void requireEnabled() {
        if (!authSettings.jwtEnabled()) {
            throw new AuthMethodDisabledException(METHOD_NAME);
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
