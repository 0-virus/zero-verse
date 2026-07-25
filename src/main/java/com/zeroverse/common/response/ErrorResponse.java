package com.zeroverse.common.response;

import java.util.List;

/**
 * 공통 실패 응답의 error 본문(PRD §4.1).
 *
 * @param code    에러 코드(REQUIREMENTS NFR-04)
 * @param message 외부 노출 메시지
 * @param details 필드 단위 상세. 없으면 빈 리스트
 */
public record ErrorResponse(String code, String message, List<FieldError> details) {

    public ErrorResponse {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, List.of());
    }

    public static ErrorResponse of(String code, String message, List<FieldError> details) {
        return new ErrorResponse(code, message, details);
    }

    /**
     * 필드 단위 검증 오류 상세.
     *
     * @param field  대상 필드명
     * @param reason 실패 사유
     */
    public record FieldError(String field, String reason) {}
}
