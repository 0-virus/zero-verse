package com.zeroverse.domain.user.entity;

/** 사용자 역할(REQUIREMENTS §4 User). Spring Security 권한명은 {@code ROLE_} 접두를 붙여 만든다. */
public enum UserRole {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
