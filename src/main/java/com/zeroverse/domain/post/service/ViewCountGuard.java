package com.zeroverse.domain.post.service;

/**
 * 조회수 중복 증가 방지 guard interface.
 * M5+ 구현(Redis 등) 교체를 위한 추상화.
 */
public interface ViewCountGuard {

    /**
     * postId를 userId(로그인) 또는 fingerprint(비로그인)로 조회했을 때 증가 허용 여부 판정.
     *
     * @param postId 게시글 ID
     * @param userId 로그인 사용자 ID (null이면 비로그인)
     * @param ipHash IP+UA 해시 (비로그인 전용)
     * @return true면 조회수 증가 허용, false면 차단
     */
    boolean canIncrement(Long postId, Long userId, String ipHash);

    /**
     * 만료된 항목 정리.
     */
    void cleanup();
}
