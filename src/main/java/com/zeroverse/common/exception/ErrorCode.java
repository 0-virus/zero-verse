package com.zeroverse.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Auth
    DUPLICATE_EMAIL("AUTH_001", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME("AUTH_002", "이미 사용 중인 닉네임입니다."),
    INVALID_CREDENTIALS("AUTH_003", "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED("AUTH_004", "로그인이 필요합니다."),
    INVALID_TOKEN("AUTH_005", "유효하지 않은 토큰입니다."),

    // User
    USER_NOT_FOUND("USER_001", "존재하지 않는 사용자입니다."),
    WRONG_PASSWORD("USER_002", "현재 비밀번호가 올바르지 않습니다."),

    // Blog
    BLOG_NOT_FOUND("BLOG_001", "존재하지 않는 블로그입니다."),
    DUPLICATE_URL_SLUG("BLOG_002", "이미 사용 중인 블로그 주소입니다."),

    // Post
    POST_NOT_FOUND("POST_001", "존재하지 않는 게시글입니다."),
    POST_ACCESS_DENIED("POST_002", "게시글에 접근할 권한이 없습니다."),

    // Category
    CATEGORY_NOT_FOUND("CAT_001", "존재하지 않는 카테고리입니다."),
    CATEGORY_DEPTH_EXCEEDED("CAT_002", "카테고리는 최대 1단계까지만 가능합니다."),

    // Comment
    COMMENT_NOT_FOUND("CMT_001", "존재하지 않는 댓글입니다."),
    COMMENT_DEPTH_EXCEEDED("CMT_002", "대댓글에는 댓글을 달 수 없습니다."),

    // Common
    FORBIDDEN("CMN_001", "접근 권한이 없습니다."),
    INVALID_INPUT("CMN_002", "입력값이 올바르지 않습니다.");

    private final String code;
    private final String message;

}
