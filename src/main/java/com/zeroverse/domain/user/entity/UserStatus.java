package com.zeroverse.domain.user.entity;

/** 사용자 상태(REQUIREMENTS §4 User). {@code SUSPENDED}는 로그인할 수 없다(FR-AUTH-02). */
public enum UserStatus {
    ACTIVE,
    SUSPENDED
}
