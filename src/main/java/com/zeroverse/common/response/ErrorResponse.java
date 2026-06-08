package com.zeroverse.common.response;

import com.zeroverse.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorResponse {
    private String code;
    private String message;
    private List<ErrorDetail> details;

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                List.of()
        );
    }

    public static ErrorResponse of(ErrorCode errorCode, List<ErrorDetail> details) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                details
        );
    }
}