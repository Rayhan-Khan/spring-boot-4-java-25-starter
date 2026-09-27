package com.mss.base.utils;

/**
 * Standard JSON envelope for every API response.
 * Create instances with the factory methods in {@link ResponseBuilder}.
 *
 * @param status  {@code "success"} or {@code "error"}
 * @param message optional human-readable message
 * @param data    response payload (success only)
 * @param meta    pagination details (paginated lists only)
 * @param errors  error details (errors only)
 */
public record ApiResponse<T>(String status, String message, T data, Object meta, Object errors) {
}
