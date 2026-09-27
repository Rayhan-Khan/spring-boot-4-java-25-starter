package com.mss.base;

import com.google.cloud.storage.Storage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Both login methods enabled (JWT_ENABLED=true, Firebase mocked). Also covers general behavior:
 * migrations, JPA/QueryDSL, CORS, validation and error responses.
 */
@TestPropertySource(properties = {"JWT_ENABLED=true", "firebase.enabled=false"})
class ApplicationIntegrationTest extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

    /**
     * Present as a bean, so Firebase counts as enabled even though the real Firebase setup is skipped.
     */
    @MockitoBean
    FirebaseAuth firebaseAuth;

    @MockitoBean
    Storage storage;

    @Test
    void healthEndpointIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void firebaseRegisteredUserCanBeFoundWithUserSearch() throws Exception {
        String registerToken = firebaseToken("register");
        stubFirebaseToken(registerToken, "uid-search", "search.user@example.com", false);

        postJson("/api/v1/auth/firebase/register", """
                {"firstName": "Search", "lastName": "User",
                 "email": "search.user@example.com", "idToken": "%s"}
                """.formatted(registerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.email").value("search.user@example.com"));

        String sessionToken = firebaseToken("session");
        stubFirebaseToken(sessionToken, "uid-search", "search.user@example.com", true);

        mockMvc.perform(get("/api/v1/users")
                        .param("email", "search.user")
                        .param("sortBy", "email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sessionToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("search.user@example.com"))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void firebaseLoginAlsoReturnsTheAppsTokens() throws Exception {
        String idToken = firebaseToken("exchange");
        stubFirebaseToken(idToken, "uid-exchange", "exchange@example.com", true);

        MvcResult login = postJson("/api/v1/auth/firebase/login", """
                {"idToken": "%s"}
                """.formatted(idToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.tokens.tokenType").value("Bearer"))
                .andReturn();

        String accessToken = read(login, "$.data.tokens.accessToken");

        // The app's token and the Firebase ID token both identify the same user
        mockMvc.perform(get("/api/v1/current-user").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("exchange@example.com"));
        mockMvc.perform(get("/api/v1/current-user").header(HttpHeaders.AUTHORIZATION, bearer(idToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("exchange@example.com"));
    }

    @Test
    void firebaseLoginLinksExistingPasswordUserWithTheSameVerifiedEmail() throws Exception {
        String registered = registerWithPassword("linked@example.com", "password-123");
        String userId = read(registered, "$.data.id");

        String idToken = firebaseToken("link");
        stubFirebaseToken(idToken, "uid-link", "Linked@Example.com", true);

        postJson("/api/v1/auth/firebase/login", """
                {"idToken": "%s"}
                """.formatted(idToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(Integer.parseInt(userId)))
                .andExpect(jsonPath("$.data.firebaseUserId").value("uid-link"));

        // The password still works after linking
        postJson("/api/v1/auth/login", """
                {"email": "linked@example.com", "password": "password-123"}
                """)
                .andExpect(status().isOk());
    }

    @Test
    void unregisteredFirebaseUserIsRejectedOnProtectedEndpoints() throws Exception {
        String idToken = firebaseToken("unregistered");
        stubFirebaseToken(idToken, "uid-unregistered", "new@example.com", true);

        mockMvc.perform(get("/api/v1/current-user").header(HttpHeaders.AUTHORIZATION, bearer(idToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/current-user"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void firebaseLoginWithInvalidTokenReturns401() throws Exception {
        when(firebaseAuth.verifyIdToken("bad-token")).thenThrow(mock(FirebaseAuthException.class));

        postJson("/api/v1/auth/firebase/login", """
                {"idToken": "bad-token"}
                """)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired Firebase ID token."));
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        postJson("/api/v1/auth/register", """
                {"firstName": "", "lastName": "User", "email": "invalid@example.com", "password": "password-123"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("First name is required"))
                .andExpect(jsonPath("$.errors.firstName").value("First name is required"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        postJson("/api/v1/auth/login", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void sortingByUnknownFieldReturns400() throws Exception {
        String accessToken = read(registerWithPassword("sort@example.com", "password-123"), "$.data.tokens.accessToken");

        mockMvc.perform(get("/api/v1/users")
                        .param("sortBy", "noSuchField,desc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void unknownPathReturns404() throws Exception {
        String accessToken = read(registerWithPassword("path@example.com", "password-123"), "$.data.tokens.accessToken");

        mockMvc.perform(get("/api/v1/does-not-exist")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void corsAllowsConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/users")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void corsRejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/users")
                        .header(HttpHeaders.ORIGIN, "https://unknown.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    private void stubFirebaseToken(String idToken, String uid, String email, boolean emailVerified) throws FirebaseAuthException {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(uid);
        when(token.getEmail()).thenReturn(email);
        when(token.isEmailVerified()).thenReturn(emailVerified);
        when(firebaseAuth.verifyIdToken(idToken)).thenReturn(token);
    }
}
