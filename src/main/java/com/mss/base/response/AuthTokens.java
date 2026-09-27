package com.mss.base.response;

import lombok.Builder;
import lombok.Data;

/**
 * Tokens returned by login, registration and refresh.
 * Send the access token as {@code Authorization: Bearer <accessToken>}; when it expires,
 * exchange the refresh token at {@code POST /api/v1/auth/refresh}.
 */
@Data
@Builder
public class AuthTokens {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    /**
     * Access token lifetime in seconds.
     */
    private long expiresIn;
}
