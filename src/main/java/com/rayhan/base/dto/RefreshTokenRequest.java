package com.rayhan.base.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Refresh or logout with a refresh token.
 */
@Data
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
