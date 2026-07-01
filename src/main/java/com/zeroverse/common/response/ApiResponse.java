package com.zeroverse.common.response;

import java.time.Instant;

public record ApiResponse<T>(
    boolean success,
    T data,
    ErrorResponse error,
    String timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now().toString());
    }

    public static <T> ApiResponse<T> error(ErrorResponse error) {
        return new ApiResponse<>(false, null, error, Instant.now().toString());
    }
}
