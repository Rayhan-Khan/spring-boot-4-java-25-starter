package com.rayhan.base.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO for login request.
 */
@Data
public class FirebaseLoginRequest {
    @NotBlank(message = "Token is required.")
    private String idToken;
}

