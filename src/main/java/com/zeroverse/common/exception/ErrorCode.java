package com.zeroverse.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // Auth (NFR-04 정본)
    AUTH_001("AUTH_001", "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
    AUTH_002("AUTH_002", "토큰이 만료되었습니다.", HttpStatus.UNAUTHORIZED),
    AUTH_003("AUTH_003", "Refresh Token이 무효합니다.", HttpStatus.UNAUTHORIZED),
    AUTH_004("AUTH_004", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),

    // User
    USER_001("USER_001", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    USER_002("USER_002", "이미 존재하는 닉네임입니다.", HttpStatus.CONFLICT),
    USER_003("USER_003", "정지된 사용자입니다.", HttpStatus.FORBIDDEN),
    USER_004("USER_004", "이미 존재하는 이메일입니다.", HttpStatus.CONFLICT),
    USER_005("USER_005", "이미 가입된 이메일입니다.", HttpStatus.CONFLICT),
    USER_006("USER_006", "이미 사용 중인 닉네임입니다.", HttpStatus.CONFLICT),
    USER_007("USER_007", "현재 비밀번호가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

    // Blog
    BLOG_001("BLOG_001", "블로그를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    BLOG_002("BLOG_002", "이미 존재하는 블로그 URL입니다.", HttpStatus.CONFLICT),
    BLOG_003("BLOG_003", "블로그 URL 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    BLOG_004("BLOG_004", "블로그 설정이 이미 완료되었습니다.", HttpStatus.CONFLICT),

    // Post
    POST_001("POST_001", "게시글을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    POST_002("POST_002", "게시글에 접근할 수 없습니다.", HttpStatus.FORBIDDEN),
    POST_003("POST_003", "게시글 작성자만 수정할 수 있습니다.", HttpStatus.FORBIDDEN),

    // Category
    CAT_001("CAT_001", "카테고리를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CAT_002("CAT_002", "카테고리 깊이 제한을 초과했습니다.", HttpStatus.BAD_REQUEST),
    CAT_003("CAT_003", "기본 카테고리는 삭제할 수 없습니다.", HttpStatus.CONFLICT),
    CAT_004("CAT_004", "같은 부모 아래 카테고리명 또는 표시 순서가 중복되었습니다.", HttpStatus.CONFLICT),
    CAT_005("CAT_005", "기본 또는 잠금 카테고리는 변경 또는 삭제할 수 없습니다.", HttpStatus.CONFLICT),
    CAT_006("CAT_006", "카테고리 순서 변경 요청이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    CAT_007("CAT_007", "카테고리 타입이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    CAT_008("CAT_008", "카테고리 관리 권한이 없습니다.", HttpStatus.FORBIDDEN),

    // Universe
    UNI_001("UNI_001", "자기 자신에게 신청할 수 없습니다.", HttpStatus.BAD_REQUEST),
    UNI_002("UNI_002", "이미 신청했거나 관계가 존재합니다.", HttpStatus.CONFLICT),
    UNI_003("UNI_003", "차단된 사용자입니다.", HttpStatus.FORBIDDEN),

    // Comment
    COM_001("COM_001", "댓글을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    COM_002("COM_002", "대댓글은 1단계까지만 가능합니다.", HttpStatus.BAD_REQUEST),
    COM_003("COM_003", "댓글에 접근할 수 없습니다.", HttpStatus.FORBIDDEN),

    // Like
    LIKE_001("LIKE_001", "게시글에 접근할 수 없습니다.", HttpStatus.FORBIDDEN),

    // Notification
    NOT_001("NOT_001", "알림을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    NOT_002("NOT_002", "알림에 접근할 수 없습니다.", HttpStatus.FORBIDDEN),

    // Admin
    ADMIN_001("ADMIN_001", "관리자 권한이 필요합니다.", HttpStatus.FORBIDDEN),
    ADMIN_002("ADMIN_002", "자신의 권한을 변경할 수 없습니다.", HttpStatus.CONFLICT),

    // Upload
    UPLOAD_001("UPLOAD_001", "허용되지 않는 파일 타입입니다.", HttpStatus.BAD_REQUEST),
    UPLOAD_002("UPLOAD_002", "파일 크기가 초과되었습니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
