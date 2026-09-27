package com.mss.base.exception;

import java.io.Serial;

/**
 * Thrown when a login method is called but switched off in .env ({@code JWT_ENABLED=false}).
 * {@link GlobalExceptionHandler} returns it as 503 Service Unavailable.
 */
public class AuthMethodDisabledException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthMethodDisabledException(String method) {
        super(method + " is not enabled on this server.");
    }
}
