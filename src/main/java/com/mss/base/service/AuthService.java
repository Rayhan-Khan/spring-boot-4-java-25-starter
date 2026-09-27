package com.mss.base.service;

import com.mss.base.dto.LoginRequest;
import com.mss.base.dto.RegisterRequest;
import com.mss.base.response.AuthTokens;
import com.mss.base.response.LoginResponse;

/**
 * Email/password registration and login with the application's own tokens. Every method throws
 * {@link com.mss.base.exception.AuthMethodDisabledException} when {@code JWT_ENABLED=false}.
 */
public interface AuthService {

    /**
     * Creates a user with a hashed password and logs them in.
     */
    LoginResponse register(RegisterRequest request);

    /**
     * @throws org.springframework.security.authentication.BadCredentialsException if the email or password is wrong
     */
    LoginResponse login(LoginRequest request);

    /**
     * Exchanges a refresh token for new tokens.
     */
    AuthTokens refresh(String refreshToken);

    /**
     * Revokes the refresh token, ending that session.
     */
    void logout(String refreshToken);
}
