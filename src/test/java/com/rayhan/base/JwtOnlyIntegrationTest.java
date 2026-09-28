package com.rayhan.base;

import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JWT_ENABLED=true, FIREBASE_ENABLED=false: email/password login with the app's own tokens.
 */
@TestPropertySource(properties = {"JWT_ENABLED=true", "FIREBASE_ENABLED=false"})
class JwtOnlyIntegrationTest extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

    @Test
    void registerReturnsTokensThatOpenProtectedEndpoints() throws Exception {
        String body = registerWithPassword("Jane.Doe@Example.com", "password-123");

        assertThat(read(body, "$.data.email")).isEqualTo("jane.doe@example.com");
        assertThat(read(body, "$.data.role")).isEqualTo("USER");
        assertThat(read(body, "$.data.tokens.tokenType")).isEqualTo("Bearer");
        assertThat(read(body, "$.data.tokens.expiresIn")).isEqualTo("900");

        mockMvc.perform(get("/api/v1/current-user")
                        .header(HttpHeaders.AUTHORIZATION, bearer(read(body, "$.data.tokens.accessToken"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("jane.doe@example.com"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void registeringTheSameEmailTwiceFails() throws Exception {
        registerWithPassword("twice@example.com", "password-123");

        postJson("/api/v1/auth/register", """
                {"firstName": "Again", "lastName": "User", "email": "TWICE@example.com", "password": "password-123"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This email is already registered."));
    }

    @Test
    void tooShortPasswordIsRejected() throws Exception {
        postJson("/api/v1/auth/register", """
                {"firstName": "Short", "lastName": "Password", "email": "short@example.com", "password": "1234567"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Password must be 8 to 72 characters"));
    }

    @Test
    void loginWithCorrectPasswordReturnsTokens() throws Exception {
        registerWithPassword("login@example.com", "password-123");

        postJson("/api/v1/auth/login", """
                {"email": "LOGIN@example.com", "password": "password-123"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokens.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokens.refreshToken").isNotEmpty());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        registerWithPassword("wrong@example.com", "password-123");

        postJson("/api/v1/auth/login", """
                {"email": "wrong@example.com", "password": "not-the-password"}
                """)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
        postJson("/api/v1/auth/login", """
                {"email": "nobody@example.com", "password": "password-123"}
                """)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void refreshRotatesTokensAndReuseEndsAllSessions() throws Exception {
        String firstRefresh = read(registerWithPassword("refresh@example.com", "password-123"), "$.data.tokens.refreshToken");

        String refreshed = postJson("/api/v1/auth/refresh", refreshBody(firstRefresh))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondRefresh = read(refreshed, "$.data.refreshToken");
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);
        assertThat(read(refreshed, "$.data.accessToken")).isNotBlank();

        // Reusing the first (already exchanged) token is treated as theft...
        postJson("/api/v1/auth/refresh", refreshBody(firstRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token."));
        // ...so the newer token is revoked too
        postJson("/api/v1/auth/refresh", refreshBody(secondRefresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String refreshToken = read(registerWithPassword("logout@example.com", "password-123"), "$.data.tokens.refreshToken");

        postJson("/api/v1/auth/logout", refreshBody(refreshToken))
                .andExpect(status().isOk());
        postJson("/api/v1/auth/refresh", refreshBody(refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidAccessTokenIsRejected() throws Exception {
        String accessToken = read(registerWithPassword("tampered@example.com", "password-123"), "$.data.tokens.accessToken");
        String tampered = accessToken.substring(0, accessToken.length() - 2) + "xx";

        mockMvc.perform(get("/api/v1/current-user").header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token."));
        mockMvc.perform(get("/api/v1/current-user").header(HttpHeaders.AUTHORIZATION, bearer("not-a-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void firebaseTokensAreRejectedWhenFirebaseIsDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/current-user").header(HttpHeaders.AUTHORIZATION, bearer(firebaseToken("any"))))
                .andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/firebase/login", """
                {"idToken": "any"}
                """)
                .andExpect(status().isServiceUnavailable());
    }

    private static String refreshBody(String refreshToken) {
        return """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);
    }
}
