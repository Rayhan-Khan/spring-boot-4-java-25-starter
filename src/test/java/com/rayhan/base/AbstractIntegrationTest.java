package com.rayhan.base;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for tests that start the whole application against a throwaway PostgreSQL (Testcontainers).
 * Each subclass declares its own {@code @Container @ServiceConnection} database and picks the login methods
 * with {@code @TestPropertySource}. Skipped automatically when Docker is not available.
 * <p>
 * The DB_* values only satisfy placeholders; the container provides the real connection.
 */
@SpringBootTest(properties = {
        "DB_HOST=unused", "DB_PORT=5432", "DB_NAME=unused", "DB_USER=unused", "DB_PASSWORD=unused",
        "DB_SCHEMA=public",
        "BUCKET_NAME=test-bucket",
        "CORS_ALLOWED_ORIGINS=http://localhost:3000",
        "JWT_SECRET=" + AbstractIntegrationTest.TEST_JWT_SECRET
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
abstract class AbstractIntegrationTest {

    /**
     * Base64 of a 59-byte test-only value.
     */
    static final String TEST_JWT_SECRET = "dGVzdC1zZWNyZXQtZm9yLWludGVncmF0aW9uLXRlc3RzLW9ubHktYXQtbGVhc3QtNDgtYnl0ZXMhIQ==";

    static final String POSTGRES_IMAGE = "postgres:17-alpine";

    @Autowired
    protected MockMvc mockMvc;

    /**
     * Registers an email/password user and returns the response body.
     */
    protected String registerWithPassword(String email, String password) throws Exception {
        return postJson("/api/v1/auth/register", """
                {"firstName": "Test", "lastName": "User", "email": "%s", "password": "%s"}
                """.formatted(email, password))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    protected ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected static String read(MvcResult result, String jsonPath) throws Exception {
        return read(result.getResponse().getContentAsString(), jsonPath);
    }

    protected static String read(String json, String jsonPath) {
        Object value = JsonPath.read(json, jsonPath);
        return value == null ? null : value.toString();
    }

    /**
     * A string shaped like a Firebase ID token (RS256 header), so the bearer filter routes it to Firebase.
     * Tests stub {@code FirebaseAuth.verifyIdToken(...)} for this exact string.
     */
    protected static String firebaseToken(String name) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString(name.getBytes(StandardCharsets.UTF_8)) + ".signature";
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
