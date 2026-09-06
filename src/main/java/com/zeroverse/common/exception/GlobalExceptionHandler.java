package com.zeroverse.common.exception;

import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.common.response.ErrorResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 공통 예외 처리(PRD §4.4, REQUIREMENTS NFR-03~04).
 *
 * <p>도메인 오류는 {@link BusinessException}의 {@link ErrorCode}를 그대로 사용하고, 도메인에 귀속시킬 수
 * 없는 오류는 {@code VALIDATION_001} / {@code COMMON_404} / {@code COMMON_500}으로 분류한다(ADR-0002).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.debug("BusinessException: {} - {}", errorCode.getCode(), e.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(ErrorResponse.of(errorCode.getCode(), e.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> details = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toFieldError)
                .toList();
        ErrorCode errorCode = ErrorCode.VALIDATION_001;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(
                        ErrorResponse.of(errorCode.getCode(), errorCode.getMessage(), details)));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        BindException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleRequestBinding(Exception e) {
        ErrorCode errorCode = ErrorCode.VALIDATION_001;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(ErrorResponse.of(errorCode.getCode(), errorCode.getMessage())));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        ErrorCode errorCode = ErrorCode.COMMON_404;
        log.debug("No resource found: {}", e.getResourcePath());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(
                        ErrorResponse.of(errorCode.getCode(), errorCode.getMessage())));
    }

    /**
     * 처리되지 않은 모든 예외. 외부에는 고정 메시지만 반환하고 stack trace·SQL·내부 경로·원본 예외 메시지를
     * 노출하지 않는다(ADR-0002). 상세는 서버 로그에만 남긴다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception e) {
        ErrorCode errorCode = ErrorCode.COMMON_500;
        log.error("Unhandled exception", e);
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(
                        ErrorResponse.of(errorCode.getCode(), errorCode.getMessage())));
    }

    private static ErrorResponse.FieldError toFieldError(FieldError fieldError) {
        String reason = fieldError.getDefaultMessage();
        return new ErrorResponse.FieldError(
                fieldError.getField(), reason == null ? "올바르지 않은 값입니다." : reason);
    }
}
