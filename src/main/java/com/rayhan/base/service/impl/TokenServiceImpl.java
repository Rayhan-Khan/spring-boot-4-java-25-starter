package com.rayhan.base.service.impl;

import com.rayhan.base.entity.RefreshToken;
import com.rayhan.base.entity.User;
import com.rayhan.base.enums.UserStatus;
import com.rayhan.base.repository.RefreshTokenRepository;
import com.rayhan.base.response.AuthTokens;
import com.rayhan.base.security.JwtGenerator;
import com.rayhan.base.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Access tokens are short-lived JWTs. Refresh tokens are random values stored only as SHA-256 hashes,
 * so they can be rotated and revoked, and a database leak does not expose usable tokens.
 */
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private static final Logger logger = LoggerFactory.getLogger(TokenServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TOKEN_TYPE = "Bearer";
    private static final String INVALID_REFRESH_TOKEN = "Invalid or expired refresh token.";

    private final JwtGenerator jwtGenerator;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public AuthTokens issueTokens(User user) {
        String refreshToken = newRefreshToken();

        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setTokenHash(hash(refreshToken));
        stored.setExpiresAt(now().plus(Duration.ofMillis(jwtGenerator.refreshTokenExpirationMillis())));
        refreshTokenRepository.save(stored);

        return AuthTokens.builder()
                .accessToken(jwtGenerator.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name()))
                .refreshToken(refreshToken)
                .tokenType(TOKEN_TYPE)
                .expiresIn(jwtGenerator.accessTokenExpirationMillis() / 1000)
                .build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Reusing a refresh token that was already exchanged means it may have been stolen: all of the user's
     * refresh tokens are then revoked (kept even though the request fails, hence {@code noRollbackFor}).
     */
    @Override
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthTokens refresh(String refreshToken) {
        OffsetDateTime now = now();
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new BadCredentialsException(INVALID_REFRESH_TOKEN));

        if (stored.getRevokedAt() != null) {
            int revoked = refreshTokenRepository.revokeAllForUser(stored.getUser().getId(), now);
            logger.warn("Revoked refresh token reused for user {}; revoked {} active session(s).",
                    stored.getUser().getId(), revoked);
            throw new BadCredentialsException(INVALID_REFRESH_TOKEN);
        }
        if (!stored.isActive(now)) {
            throw new BadCredentialsException(INVALID_REFRESH_TOKEN);
        }

        User user = stored.getUser();
        if (user.getUserStatus() == UserStatus.Inactive) {
            throw new DisabledException("Your account is inactive.");
        }

        stored.setRevokedAt(now);
        return issueTokens(user);
    }

    @Override
    @Transactional
    public void revoke(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .filter(stored -> stored.getRevokedAt() == null)
                .ifPresent(stored -> stored.setRevokedAt(now()));
    }

    private static String newRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
