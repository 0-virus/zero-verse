package com.zeroverse.common.response;

import com.zeroverse.common.exception.ErrorResponse;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorResponse error;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.data = data;
        response.error = null;
        response.timestamp = LocalDateTime.now();
        return response;
    }

    public static <T> ApiResponse<T> fail(ErrorResponse error) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.data = null;
        response.error = error;
        response.timestamp = LocalDateTime.now();
        return response;
    }
}
