package com.zeroverse.common.exception;

import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.common.response.ErrorResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        ErrorResponse errorResponse = ErrorResponse.of(errorCode.getCode(), errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
            .body(ApiResponse.error(errorResponse));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> details = new ArrayList<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
            details.add(new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
        );
        ErrorResponse errorResponse = ErrorResponse.of("VALIDATION_001", "요청 파라미터가 올바르지 않습니다.", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(errorResponse));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoHandlerFound(NoHandlerFoundException e) {
        ErrorResponse errorResponse = ErrorResponse.of("404", "요청한 리소스를 찾을 수 없습니다.");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(errorResponse));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        ErrorResponse errorResponse = ErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(errorResponse));
    }
}
