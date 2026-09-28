package com.rayhan.base.exception;

import java.io.Serial;

/**
 * Thrown when a feature needs Firebase (login, registration, file storage) but the application was started
 * with {@code FIREBASE_ENABLED=false}. {@link GlobalExceptionHandler} returns it as 503 Service Unavailable.
 */
public class FirebaseNotConfiguredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public FirebaseNotConfiguredException() {
        super("Firebase is not configured on this server.");
    }
}
