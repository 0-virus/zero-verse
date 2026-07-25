package com.zeroverse.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드 중앙 관리(REQUIREMENTS NFR-04).
 *
 * <p>도메인 오류는 도메인 prefix를 사용한다. 특정 도메인에 귀속시킬 수 없는 오류는 {@code VALIDATION_*} /
 * {@code COMMON_*}을 사용한다(ADR-0002, 2026-07-25 승인).
 *
 * <p>여기에 없는 코드를 임의로 발명하지 않는다. 새 코드는 REQUIREMENTS NFR-04 개정을 거친다.
 */
public enum ErrorCode {

    // --- 비도메인 공통 (ADR-0002) ---
    VALIDATION_001("VALIDATION_001", HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    COMMON_404("COMMON_404", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    /** 처리되지 않은 서버 오류. 내부 예외 정보를 절대 노출하지 않는 고정 메시지를 사용한다. */
    COMMON_500("COMMON_500", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // --- AUTH ---
    AUTH_001("AUTH_001", HttpStatus.UNAUTHORIZED, "로그인에 실패했습니다."),
    AUTH_002("AUTH_002", HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    AUTH_003("AUTH_003", HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),

    // --- USER ---
    USER_001("USER_001", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    USER_002("USER_002", HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    USER_003("USER_003", HttpStatus.FORBIDDEN, "정지된 사용자입니다."),

    // --- BLOG ---
    BLOG_001("BLOG_001", HttpStatus.NOT_FOUND, "블로그를 찾을 수 없습니다."),
    BLOG_002("BLOG_002", HttpStatus.CONFLICT, "이미 사용 중인 주소입니다."),
    BLOG_003("BLOG_003", HttpStatus.BAD_REQUEST, "주소 형식이 올바르지 않습니다."),

    // --- POST ---
    POST_001("POST_001", HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    POST_002("POST_002", HttpStatus.FORBIDDEN, "게시글에 접근할 수 없습니다."),
    POST_003("POST_003", HttpStatus.FORBIDDEN, "작성자만 수정할 수 있습니다."),

    // --- CATEGORY ---
    CAT_001("CAT_001", HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    CAT_002("CAT_002", HttpStatus.BAD_REQUEST, "카테고리 깊이 제한을 초과했습니다."),
    CAT_003("CAT_003", HttpStatus.BAD_REQUEST, "기본 카테고리는 삭제할 수 없습니다."),

    // --- UNIVERSE ---
    UNI_001("UNI_001", HttpStatus.BAD_REQUEST, "자기 자신에게 신청할 수 없습니다."),
    UNI_002("UNI_002", HttpStatus.CONFLICT, "이미 신청한 상대입니다."),
    UNI_003("UNI_003", HttpStatus.FORBIDDEN, "차단된 상태입니다."),

    // --- COMMENT ---
    COM_001("COM_001", HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    COM_002("COM_002", HttpStatus.BAD_REQUEST, "대댓글 깊이 제한을 초과했습니다."),
    COM_003("COM_003", HttpStatus.FORBIDDEN, "댓글에 대한 권한이 없습니다."),

    // --- LIKE ---
    LIKE_001("LIKE_001", HttpStatus.FORBIDDEN, "게시글에 접근할 수 없습니다."),

    // --- NOTIFICATION ---
    NOT_001("NOT_001", HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    NOT_002("NOT_002", HttpStatus.FORBIDDEN, "알림에 대한 권한이 없습니다."),

    // --- ADMIN ---
    ADMIN_001("ADMIN_001", HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),
    ADMIN_002("ADMIN_002", HttpStatus.BAD_REQUEST, "자기 자신의 권한은 변경할 수 없습니다."),

    // --- UPLOAD ---
    UPLOAD_001("UPLOAD_001", HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    UPLOAD_002("UPLOAD_002", HttpStatus.BAD_REQUEST, "파일 크기가 제한을 초과했습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
