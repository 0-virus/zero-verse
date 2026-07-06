package com.zeroverse.domain.post.entity;

/**
 * 게시글 공개범위 enum.
 * PUBLIC: 공개(누구나 조회 가능)
 * PRIVATE: 비공개(작성자만 조회 가능)
 * UNIVERSE: 친구에게만 공개(M4a는 로그인 사용자 임시 허용, M5에서 발견자 관계 검증으로 교체)
 */
public enum Visibility {
    PUBLIC,
    PRIVATE,
    UNIVERSE
}
