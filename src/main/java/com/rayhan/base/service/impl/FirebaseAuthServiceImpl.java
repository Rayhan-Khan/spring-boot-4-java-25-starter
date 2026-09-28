package com.rayhan.base.service.impl;

import com.rayhan.base.config.AuthSettings;
import com.rayhan.base.dto.FirebaseRegisterRequest;
import com.rayhan.base.entity.User;
import com.rayhan.base.enums.UserStatus;
import com.rayhan.base.exception.CustomMessagePresentException;
import com.rayhan.base.exception.EmailNotVerifiedException;
import com.rayhan.base.exception.FirebaseNotConfiguredException;
import com.rayhan.base.repository.UserRepository;
import com.rayhan.base.response.AuthTokens;
import com.rayhan.base.response.LoginResponse;
import com.rayhan.base.response.UserRegistrationResponse;
import com.rayhan.base.service.FirebaseAuthService;
import com.rayhan.base.service.TokenService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Firebase registration and login.
 */
@Service
@RequiredArgsConstructor
public class FirebaseAuthServiceImpl implements FirebaseAuthService {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseAuthServiceImpl.class);

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final AuthSettings authSettings;

    /**
     * Empty when the application runs with {@code FIREBASE_ENABLED=false}.
     */
    private final ObjectProvider<FirebaseAuth> firebaseAuthProvider;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public UserRegistrationResponse registerUser(FirebaseRegisterRequest request) {
        try {
            FirebaseToken decodedToken = verifyToken(request.getIdToken());
            User savedUser = createOrUpdateUser(decodedToken, request);

            logger.info("User successfully saved with ID: {}", savedUser.getId());

            return UserRegistrationResponse.builder()
                    .id(savedUser.getId())
                    .firstName(savedUser.getFirstName())
                    .lastName(savedUser.getLastName())
                    .email(savedUser.getEmail())
                    .isEmailVerified(savedUser.getIsEmailVerified())
                    .firebaseUserId(savedUser.getFirebaseUserId())
                    .build();
        } catch (FirebaseNotConfiguredException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error occurred during user registration: {}", e.getMessage(), e);
            throw new CustomMessagePresentException(e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String resendVerificationEmail(String email) {
        logger.info("Attempting to resend verification email for: {}", email);
        userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> {
                    logger.warn("User with email {} not found", email);
                    return new CustomMessagePresentException("User with email not found");
                });

        FirebaseAuth firebaseAuth = firebaseAuth();
        try {
            String verificationLink = firebaseAuth.generateEmailVerificationLink(email);
            logger.debug("Generated verification link for {}", email);
            return verificationLink;
        } catch (Exception ex) {
            logger.error("Error sending verification email for {}: {}", email, ex.getMessage(), ex);
            throw new CustomMessagePresentException(ex.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public LoginResponse login(String idToken) {
        try {
            logger.debug("Starting Firebase login process.");

            FirebaseToken decodedToken = verifyToken(idToken);
            if (!decodedToken.isEmailVerified()) {
                throw new EmailNotVerifiedException("Please verify your email.");
            }

            User user = createOrUpdateUser(decodedToken, null);
            AuthTokens tokens = authSettings.jwtEnabled() ? tokenService.issueTokens(user) : null;
            logger.info("Firebase login completed for user {}.", user.getId());

            return LoginResponse.from(user, tokens);
        } catch (FirebaseAuthException e) {
            logger.warn("Firebase authentication failed: {}", e.getMessage());
            throw new BadCredentialsException("Invalid or expired Firebase ID token.", e);
        }
    }

    private FirebaseAuth firebaseAuth() {
        FirebaseAuth firebaseAuth = firebaseAuthProvider.getIfAvailable();
        if (firebaseAuth == null) {
            throw new FirebaseNotConfiguredException();
        }
        return firebaseAuth;
    }

    private FirebaseToken verifyToken(String idToken) throws FirebaseAuthException {
        FirebaseToken decodedToken = firebaseAuth().verifyIdToken(idToken);
        logger.debug("Verified Firebase ID token for user {}", decodedToken.getUid());
        return decodedToken;
    }

    /**
     * Finds the user by Firebase UID. Otherwise links the Firebase account to an existing user with the same
     * email (only if Firebase has verified that email), or creates a new user.
     */
    private User createOrUpdateUser(FirebaseToken decodedToken, FirebaseRegisterRequest request) {
        String email = decodedToken.getEmail();
        boolean isEmailVerified = decodedToken.isEmailVerified();
        String firebaseUserId = decodedToken.getUid();

        if (email == null || email.isEmpty()) {
            logger.error("Email is missing in the ID token.");
            throw new IllegalArgumentException("Email is required but not provided by the provider.");
        }

        String normalizedEmail = email.toLowerCase(Locale.ROOT);

        Optional<User> existingUser = userRepository.findByFirebaseUserId(firebaseUserId);
        if (existingUser.isEmpty()) {
            existingUser = userRepository.findByEmailIgnoreCase(normalizedEmail);
            if (existingUser.isPresent()) {
                if (!isEmailVerified) {
                    throw new CustomMessagePresentException(
                            "This email is already registered. Verify it in Firebase to link the accounts.");
                }
                logger.info("Linking Firebase account to existing user {}", existingUser.get().getId());
            }
        }

        User user = existingUser.orElseGet(User::new);
        boolean isNewUser = user.getId() == null;
        user.setFirebaseUserId(firebaseUserId);
        user.setEmail(normalizedEmail);
        user.setIsEmailVerified(isEmailVerified);
        user.setUserStatus(isEmailVerified ? UserStatus.Active : UserStatus.Inactive);

        if (request != null) {
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());
        } else if (isNewUser) {
            // Names come from the Firebase profile only for new users, so later logins do not
            // overwrite names the user has changed
            applyDisplayName(user, decodedToken.getName(), normalizedEmail);
        }

        return userRepository.save(user);
    }

    private static void applyDisplayName(User user, String displayName, String email) {
        if (displayName == null || displayName.isBlank()) {
            user.setFirstName(email.substring(0, email.indexOf('@')));
            user.setLastName("");
            return;
        }
        String[] nameParts = displayName.trim().split("\\s+");
        if (nameParts.length > 1) {
            user.setFirstName(String.join(" ", Arrays.copyOf(nameParts, nameParts.length - 1)));
            user.setLastName(nameParts[nameParts.length - 1]);
        } else {
            user.setFirstName(nameParts[0]);
            user.setLastName("");
        }
    }
}
