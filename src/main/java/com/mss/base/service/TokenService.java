package com.mss.base.service;

import com.mss.base.entity.User;
import com.mss.base.response.AuthTokens;

/**
 * Issues the application's access tokens (JWT) and manages refresh tokens.
 */
public interface TokenService {

    /**
     * Issues a new access token and a new refresh token for the user.
     */
    AuthTokens issueTokens(User user);

    /**
     * Exchanges a valid refresh token for new tokens. The old refresh token stops working (rotation).
     *
     * @throws org.springframework.security.authentication.BadCredentialsException if the token is unknown,
     *                                                                              expired or already used
     */
    AuthTokens refresh(String refreshToken);

    /**
     * Revokes a refresh token (logout). Unknown or already revoked tokens are ignored.
     */
    void revoke(String refreshToken);
}
