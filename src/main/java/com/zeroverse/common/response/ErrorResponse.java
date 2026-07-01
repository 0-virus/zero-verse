package com.zeroverse.common.response;

import java.util.List;

public record ErrorResponse(
    String code,
    String message,
    List<FieldError> details
) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }

    public static ErrorResponse of(String code, String message, List<FieldError> details) {
        return new ErrorResponse(code, message, details);
    }

    public record FieldError(String field, String reason) {
    }
}
