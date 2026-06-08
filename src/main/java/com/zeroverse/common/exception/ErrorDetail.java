package com.zeroverse.common.exception;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorDetail {
    private String field;
    private String reason;

    public static ErrorDetail of(String field, String reason) {
        return new ErrorDetail(field, reason);
    }
}
