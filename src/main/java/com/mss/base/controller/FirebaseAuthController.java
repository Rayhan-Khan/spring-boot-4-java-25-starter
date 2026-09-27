package com.mss.base.controller;

import com.mss.base.dto.FirebaseLoginRequest;
import com.mss.base.dto.FirebaseRegisterRequest;
import com.mss.base.entity.User;
import com.mss.base.exception.CustomMessagePresentException;
import com.mss.base.response.LoginResponse;
import com.mss.base.response.UserRegistrationResponse;
import com.mss.base.service.FirebaseAuthService;
import com.mss.base.service.UserService;
import com.mss.base.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

import static com.mss.base.constant.ValidatorConstants.ALREADY_EXIST;
import static com.mss.base.utils.ResponseBuilder.error;
import static com.mss.base.utils.ResponseBuilder.success;
import static org.springframework.http.ResponseEntity.ok;

/**
 * Firebase registration and login (enabled with {@code FIREBASE_ENABLED=true}). Clients sign in with the
 * Firebase SDK and send the Firebase ID token here.
 */
@RestController
@RequestMapping("/api/v1/auth/firebase")
@RequiredArgsConstructor
@Tag(name = "Auth (Firebase)", description = "Login with Firebase ID tokens")
public class FirebaseAuthController {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseAuthController.class);

    private final FirebaseAuthService firebaseAuthService;
    private final UserService userService;

    @Operation(summary = "Register a new user", description = "Registers a Firebase user in this backend.")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserRegistrationResponse>> registerUser(@Valid @RequestBody FirebaseRegisterRequest request) {
        Optional<User> users = userService.findByEmailExist(request.getEmail());
        if (users.isPresent())
            throw new CustomMessagePresentException("This email " + ALREADY_EXIST);
        UserRegistrationResponse response = firebaseAuthService.registerUser(request);
        return ok(success(response, "User registered successfully. Email verification sent."));
    }

    @Operation(summary = "Resend email verification link", description = "Resends a verification email to an existing user.")
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<String>> resendVerificationEmail(@RequestParam String email) {
        try {
            String verificationLink = firebaseAuthService.resendVerificationEmail(email);
            logger.info("Verification email link successfully generated for: {}", email);
            return ok(success(verificationLink));
        } catch (CustomMessagePresentException ex) {
            logger.error("Error while resending verification email for {}: {}", email, ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(error(null, ex.getMessage()));
        }
    }

    @Operation(summary = "Log in with a Firebase ID token",
            description = "Creates or updates the user. When JWT_ENABLED=true, also returns the app's access and refresh tokens.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody FirebaseLoginRequest request) {
        try {
            LoginResponse loginResponse = firebaseAuthService.login(request.getIdToken());
            return ok(success(loginResponse, "User successfully authenticated and saved."));
        } catch (CustomMessagePresentException ex) {
            logger.error("Error during Firebase login: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(error(null, ex.getMessage()));
        }
    }
}
