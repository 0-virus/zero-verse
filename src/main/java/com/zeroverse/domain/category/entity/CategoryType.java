package com.zeroverse.domain.category.entity;

/**
 * 카테고리 타입(PRD §9-H).
 *
 * <p>{@code SERIES}는 제거됐다. {@code DEFAULT}(미분류)와 {@code LOCKED}는 변경·삭제할 수 없다.
 */
public enum CategoryType {
    /** 미분류. 블로그당 1개이며 변경·삭제 불가. */
    DEFAULT,
    GENERAL,
    /** 잠금. 변경·삭제 불가. */
    LOCKED
}
