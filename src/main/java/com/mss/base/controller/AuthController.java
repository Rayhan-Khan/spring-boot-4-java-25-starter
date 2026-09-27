package com.mss.base.controller;

import com.mss.base.annotation.ValidEmail;
import com.mss.base.dto.LoginRequest;
import com.mss.base.dto.RefreshTokenRequest;
import com.mss.base.dto.RegisterRequest;
import com.mss.base.response.AuthTokens;
import com.mss.base.response.CheckEmailResponse;
import com.mss.base.response.LoginResponse;
import com.mss.base.service.AuthService;
import com.mss.base.service.CheckEmailService;
import com.mss.base.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.mss.base.utils.ResponseBuilder.error;
import static com.mss.base.utils.ResponseBuilder.success;
import static org.springframework.http.ResponseEntity.ok;

/**
 * Email/password authentication with the application's own tokens (enabled with {@code JWT_ENABLED=true}).
 * Firebase login is in {@link FirebaseAuthController}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Email/password login with access and refresh tokens")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final CheckEmailService checkEmailService;

    @Operation(summary = "Register with email and password",
            description = "Creates a user and returns access and refresh tokens. Needs JWT_ENABLED=true.")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<LoginResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ok(success(authService.register(request), "User registered successfully."));
    }

    @Operation(summary = "Log in with email and password",
            description = "Returns access and refresh tokens. Send the access token as 'Authorization: Bearer <token>'.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ok(success(authService.login(request), "User successfully authenticated."));
    }

    @Operation(summary = "Refresh tokens",
            description = "Exchanges a refresh token for new tokens. The old refresh token stops working.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokens>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ok(success(authService.refresh(request.getRefreshToken())));
    }

    @Operation(summary = "Log out", description = "Revokes the refresh token, ending that session.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ok(success(null, "Logged out."));
    }

    @Operation(summary = "Check if Email is Already Registered", description = "Verifies whether the given email exists in the system.")
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<CheckEmailResponse>> checkEmail(@RequestParam @Valid @ValidEmail String email) {
        try {
            boolean exists = checkEmailService.emailExists(email);
            CheckEmailResponse response = new CheckEmailResponse();
            response.setExists(exists);
            return ok(success(response, "Email existence check successful."));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid email provided: {}", email, e);
            return ResponseEntity.badRequest().body(error("Invalid email format."));
        } catch (DataAccessException e) {
            logger.error("Database error while checking email: {}", email, e);
            return ResponseEntity.internalServerError().body(error("Database error. Please try again later."));
        }
    }
}
