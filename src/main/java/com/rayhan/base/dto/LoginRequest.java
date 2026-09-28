package com.rayhan.base.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Email/password login.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
