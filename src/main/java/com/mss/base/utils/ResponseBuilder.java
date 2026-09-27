package com.mss.base.utils;

import com.mss.base.constant.ResponseStatus;

/**
 * Factory methods for {@link ApiResponse}.
 */
public final class ResponseBuilder {

    private ResponseBuilder() {
    }

    public static ApiResponse<Object> paginatedSuccess(PaginatedResponse page) {
        return paginatedSuccess(page, null);
    }

    public static ApiResponse<Object> paginatedSuccess(PaginatedResponse page, String message) {
        return new ApiResponse<>(ResponseStatus.SUCCESS, message, page.getList(), page.getMeta(), null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(data, null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(ResponseStatus.SUCCESS, message, data, null, null);
    }

    public static <T> ApiResponse<T> error(Object errors) {
        return error(errors, null);
    }

    public static <T> ApiResponse<T> error(Object errors, String message) {
        return new ApiResponse<>(ResponseStatus.ERROR, message, null, null, errors);
    }
}
