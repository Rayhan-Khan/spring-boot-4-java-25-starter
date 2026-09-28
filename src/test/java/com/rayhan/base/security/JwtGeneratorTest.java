package com.rayhan.base.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtGeneratorTest {

    private static final String SECRET = base64("test-secret-that-is-at-least-32-bytes-long");

    private final JwtGenerator jwtGenerator = new JwtGenerator(SECRET, 60_000, 120_000);

    @Test
    void accessTokenCanBeReadBack() {
        String token = jwtGenerator.generateAccessToken(42, "jane@example.com", "ADMIN");

        JwtGenerator.AccessTokenClaims claims = jwtGenerator.parseAccessToken(token);
        assertThat(claims.userId()).isEqualTo(42);
        assertThat(claims.email()).isEqualTo("jane@example.com");
        assertThat(claims.role()).isEqualTo("ADMIN");
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtGenerator other = new JwtGenerator(base64("another-secret-that-is-also-32-bytes-long"), 60_000, 120_000);
        String token = other.generateAccessToken(1, "jane@example.com", "USER");

        assertThatThrownBy(() -> jwtGenerator.parseAccessToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtGenerator.generateAccessToken(1, "jane@example.com", "USER");
        int signatureStart = token.lastIndexOf('.') + 1;
        char first = token.charAt(signatureStart);
        String tampered = token.substring(0, signatureStart) + (first == 'A' ? 'B' : 'A') + token.substring(signatureStart + 1);

        assertThatThrownBy(() -> jwtGenerator.parseAccessToken(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtGenerator alreadyExpired = new JwtGenerator(SECRET, -1_000, -1_000);
        String token = alreadyExpired.generateAccessToken(1, "jane@example.com", "USER");

        assertThatThrownBy(() -> jwtGenerator.parseAccessToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void lifetimesComeFromSettings() {
        assertThat(jwtGenerator.accessTokenExpirationMillis()).isEqualTo(60_000);
        assertThat(jwtGenerator.refreshTokenExpirationMillis()).isEqualTo(120_000);
    }

    @Test
    void missingSecretFailsWithClearMessage() {
        JwtGenerator withoutSecret = new JwtGenerator("", 60_000, 120_000);

        assertThatThrownBy(withoutSecret::requireValidSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void secretThatIsNotBase64IsRejected() {
        JwtGenerator notBase64 = new JwtGenerator("your_base64_secret_here", 60_000, 120_000);

        assertThatThrownBy(notBase64::requireValidSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void secretShorterThan256BitsIsRejected() {
        JwtGenerator tooShort = new JwtGenerator(base64("only-16-bytes!!!"), 60_000, 120_000);

        assertThatThrownBy(tooShort::requireValidSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
