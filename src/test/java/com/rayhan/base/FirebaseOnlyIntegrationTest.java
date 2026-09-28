package com.rayhan.base;

import com.rayhan.base.security.JwtGenerator;
import com.google.cloud.storage.Storage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JWT_ENABLED=false with Firebase (mocked): Firebase login only, as before JWT support was added.
 */
@TestPropertySource(properties = {"JWT_ENABLED=false", "firebase.enabled=false"})
class FirebaseOnlyIntegrationTest extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

    @MockitoBean
    FirebaseAuth firebaseAuth;

    @MockitoBean
    Storage storage;

    @Test
    void firebaseLoginWorksWithoutAppTokens() throws Exception {
        String idToken = firebaseToken("firebase-only");
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("uid-firebase-only");
        when(token.getEmail()).thenReturn("firebase.only@example.com");
        when(token.isEmailVerified()).thenReturn(true);
        when(firebaseAuth.verifyIdToken(idToken)).thenReturn(token);

        postJson("/api/v1/auth/firebase/login", """
                {"idToken": "%s"}
                """.formatted(idToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("firebase.only@example.com"))
                .andExpect(jsonPath("$.data.tokens").doesNotExist());

        mockMvc.perform(get("/api/v1/current-user").header(HttpHeaders.AUTHORIZATION, bearer(idToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("firebase.only@example.com"));
    }

    @Test
    void emailAndPasswordEndpointsReturn503() throws Exception {
        postJson("/api/v1/auth/login", """
                {"email": "someone@example.com", "password": "password-123"}
                """)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Email and password login is not enabled on this server."));
        postJson("/api/v1/auth/register", """
                {"firstName": "A", "lastName": "B", "email": "someone@example.com", "password": "password-123"}
                """)
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void appTokensAreRejectedWhenJwtIsDisabled() throws Exception {
        String appToken = new JwtGenerator(TEST_JWT_SECRET, 60_000, 120_000)
                .generateAccessToken(1, "someone@example.com", "USER");

        mockMvc.perform(get("/api/v1/current-user").header(HttpHeaders.AUTHORIZATION, bearer(appToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidFirebaseTokenIsRejected() throws Exception {
        String idToken = firebaseToken("invalid");
        when(firebaseAuth.verifyIdToken(idToken)).thenThrow(mock(FirebaseAuthException.class));

        mockMvc.perform(get("/api/v1/current-user").header(HttpHeaders.AUTHORIZATION, bearer(idToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired Firebase ID token."));
    }
}
