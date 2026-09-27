package com.mss.base;

import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JWT_ENABLED=false and FIREBASE_ENABLED=false, with no Firebase beans at all: the application still starts,
 * public endpoints work, login endpoints answer 503 and protected endpoints 401.
 */
@TestPropertySource(properties = {"JWT_ENABLED=false", "FIREBASE_ENABLED=false"})
class NoLoginMethodIntegrationTest extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

    @Test
    void applicationStartsAndIsHealthy() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void publicEndpointsStillWork() throws Exception {
        mockMvc.perform(get("/api/v1/auth/check-email").param("email", "nobody@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(false));
    }

    @Test
    void loginEndpointsReturn503() throws Exception {
        postJson("/api/v1/auth/login", """
                {"email": "someone@example.com", "password": "password-123"}
                """)
                .andExpect(status().isServiceUnavailable());
        postJson("/api/v1/auth/firebase/login", """
                {"idToken": "any-token"}
                """)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Firebase is not configured on this server."));
    }

    @Test
    void protectedEndpointsReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/current-user"))
                .andExpect(status().isUnauthorized());
    }
}
