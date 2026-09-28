package com.rayhan.base.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * Creates and verifies the application's own access tokens: HMAC-signed JWTs whose subject is the user ID.
 * <p>
 * The signing secret comes from {@code app.jwt.secret} ({@code JWT_SECRET} in .env): a Base64 value of at least
 * 256 bits, e.g. from {@code openssl rand -base64 48}. Refresh tokens are not JWTs; see
 * {@link com.rayhan.base.service.TokenService}.
 */
@Component
public class JwtGenerator {

    private static final int MIN_SECRET_BYTES = 32;
    private static final String SECRET_REQUIREMENT =
            "JWT_SECRET must be a Base64 value of at least 256 bits (generate one with: openssl rand -base64 48).";
    private static final String EMAIL_CLAIM = "email";
    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final String secret;
    private final long accessTokenExpire;
    private final long refreshTokenExpire;
    private volatile SecretKey signingKey;

    public JwtGenerator(@Value("${app.jwt.secret:}") String secret,
                        @Value("${app.jwt.access-token-expiration}") long accessTokenExpire,
                        @Value("${app.jwt.refresh-token-expiration}") long refreshTokenExpire) {
        this.secret = secret;
        this.accessTokenExpire = accessTokenExpire;
        this.refreshTokenExpire = refreshTokenExpire;
    }

    /**
     * Claims read from a valid access token.
     */
    public record AccessTokenClaims(Integer userId, String email, String role) {
    }

    public String generateAccessToken(Integer userId, String email, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(EMAIL_CLAIM, email)
                .claim(ROLE_CLAIM, role)
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpire))
                .signWith(signingKey())
                .compact();
    }

    /**
     * Verifies the signature and expiry of an access token and returns its claims.
     *
     * @throws JwtException             if the token is invalid, expired or not an access token
     * @throws IllegalArgumentException if the token is empty or its subject is not a user ID
     */
    public AccessTokenClaims parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtException("Not an access token");
        }
        return new AccessTokenClaims(Integer.valueOf(claims.getSubject()),
                claims.get(EMAIL_CLAIM, String.class),
                claims.get(ROLE_CLAIM, String.class));
    }

    /**
     * Access token lifetime in milliseconds ({@code JWT_ACCESS_EXPIRATION}).
     */
    public long accessTokenExpirationMillis() {
        return accessTokenExpire;
    }

    /**
     * Refresh token lifetime in milliseconds ({@code JWT_REFRESH_EXPIRATION}).
     */
    public long refreshTokenExpirationMillis() {
        return refreshTokenExpire;
    }

    /**
     * Fails fast when the secret is missing or too weak. Called at startup when JWT login is enabled.
     *
     * @throws IllegalStateException if JWT_SECRET is not a Base64 value of at least 256 bits
     */
    public void requireValidSecret() {
        signingKey();
    }

    private SecretKey signingKey() {
        if (signingKey == null) {
            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException(SECRET_REQUIREMENT);
            }
            byte[] keyBytes;
            try {
                keyBytes = Base64.getDecoder().decode(secret.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(SECRET_REQUIREMENT, e);
            }
            if (keyBytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(SECRET_REQUIREMENT);
            }
            signingKey = Keys.hmacShaKeyFor(keyBytes);
        }
        return signingKey;
    }
}
