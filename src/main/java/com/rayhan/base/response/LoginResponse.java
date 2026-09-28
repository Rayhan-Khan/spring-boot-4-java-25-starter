package com.rayhan.base.response;

import com.rayhan.base.entity.User;
import lombok.Builder;
import lombok.Data;

/**
 * DTO for login and registration responses: the user, plus the app's tokens when JWT login is enabled.
 */
@Data
@Builder
public class LoginResponse {
    private Integer id;
    private String firstName;
    private String lastName;
    private String email;
    private Boolean isEmailVerified;
    private String firebaseUserId;
    private String role;
    /**
     * The app's access and refresh tokens; null when JWT login is disabled (Firebase-only mode).
     */
    private AuthTokens tokens;

    public static LoginResponse from(User user, AuthTokens tokens) {
        return LoginResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .isEmailVerified(user.getIsEmailVerified())
                .firebaseUserId(user.getFirebaseUserId())
                .role(user.getRole().name())
                .tokens(tokens)
                .build();
    }
}
