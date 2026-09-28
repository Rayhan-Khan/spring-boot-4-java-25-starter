package com.rayhan.base.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the Bean Validation rules on the auth request DTOs.
 * Runs without Spring, a database or Firebase.
 */
class AuthRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void validRegisterRequestHasNoViolations() {
        assertThat(validator.validate(registerRequest())).isEmpty();
    }

    @Test
    void registerRejectsBlankFirstName() {
        RegisterRequest request = registerRequest();
        request.setFirstName(" ");

        assertThat(messages(validator.validate(request))).containsExactly("First name is required");
    }

    @Test
    void registerRejectsInvalidEmail() {
        RegisterRequest request = registerRequest();
        request.setEmail("not-an-email");

        assertThat(messages(validator.validate(request))).containsExactly("Invalid email format");
    }

    @Test
    void registerRejectsPasswordsOutside8To72Characters() {
        RegisterRequest request = registerRequest();
        request.setPassword("1234567");
        assertThat(messages(validator.validate(request))).containsExactly("Password must be 8 to 72 characters");

        request.setPassword("a".repeat(73));
        assertThat(messages(validator.validate(request))).containsExactly("Password must be 8 to 72 characters");
    }

    @Test
    void loginRequiresEmailAndPassword() {
        assertThat(messages(validator.validate(new LoginRequest())))
                .containsExactlyInAnyOrder("Email is required", "Password is required");
    }

    @Test
    void firebaseRegistrationRejectsLastNameOver100Characters() {
        FirebaseRegisterRequest request = new FirebaseRegisterRequest();
        request.setFirstName("Jane");
        request.setLastName("a".repeat(101));
        request.setEmail("jane.doe@example.com");
        request.setIdToken("firebase-id-token");

        assertThat(messages(validator.validate(request))).containsExactly("Last name must not exceed 100 characters");
    }

    @Test
    void firebaseLoginRequiresIdToken() {
        assertThat(messages(validator.validate(new FirebaseLoginRequest()))).containsExactly("Token is required.");
    }

    @Test
    void refreshRequiresRefreshToken() {
        assertThat(messages(validator.validate(new RefreshTokenRequest()))).containsExactly("Refresh token is required");
    }

    private static RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail("jane.doe@example.com");
        request.setPassword("password-123");
        return request;
    }

    private static List<String> messages(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(ConstraintViolation::getMessage).toList();
    }
}
