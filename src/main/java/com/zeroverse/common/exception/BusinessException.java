package com.zeroverse.common.exception;

/**
 * 도메인 규칙 위반을 나타내는 런타임 예외.
 *
 * <p>{@link ErrorCode}가 HTTP status와 외부 메시지를 함께 결정한다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
