package com.mss.base.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;

/**
 * Exception thrown when a requested resource is not found.
 *
 * <p>This exception is annotated with {@code @ResponseStatus(HttpStatus.NOT_FOUND)},
 * which means when this exception is thrown from a controller method, Spring will
 * automatically return an HTTP 404 (Not Found) response to the client.</p>
 *
 * <p>This is typically used in RESTful applications to indicate that the requested
 * resource (such as an entity with a specific ID) does not exist in the system.</p>
 */
@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new ResourceNotFoundException with no detail message.
     */
    public ResourceNotFoundException() {
    }

    /**
     * Constructs a new ResourceNotFoundException with the specified detail message.
     *
     * @param message the detail message that describes the reason for the exception
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}