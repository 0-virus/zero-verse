package com.zeroverse.domain.post.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인메모리 기반 조회수 중복 증가 방지.
 * 24시간 TTL + 정기 cleanup(1시간마다).
 * 키: postId:userId (로그인) 또는 postId:ipHash (비로그인)
 */
@Component
public class InMemoryViewCountGuard implements ViewCountGuard {

    private static final long TTL_MILLIS = 24 * 60 * 60 * 1000; // 24 hours
    private static final long CLEANUP_INTERVAL = 60 * 60 * 1000; // 1 hour

    private final Map<String, Instant> viewTimestamps = new ConcurrentHashMap<>();

    @Override
    public boolean canIncrement(Long postId, Long userId, String ipHash) {
        String key = buildKey(postId, userId, ipHash);
        Instant lastView = viewTimestamps.get(key);

        if (lastView == null) {
            // 처음 조회
            viewTimestamps.put(key, Instant.now());
            return true;
        }

        Instant now = Instant.now();
        if (now.toEpochMilli() - lastView.toEpochMilli() >= TTL_MILLIS) {
            // 24시간 경과 후 재조회
            viewTimestamps.put(key, now);
            return true;
        }

        // 24시간 이내 재조회
        return false;
    }

    @Override
    @Scheduled(fixedRate = CLEANUP_INTERVAL)
    public void cleanup() {
        Instant now = Instant.now();
        long expiryThreshold = now.toEpochMilli() - TTL_MILLIS;

        viewTimestamps.entrySet().removeIf(entry ->
            entry.getValue().toEpochMilli() < expiryThreshold
        );
    }

    private String buildKey(Long postId, Long userId, String ipHash) {
        if (userId != null) {
            return postId + ":" + userId;
        }
        return postId + ":" + ipHash;
    }
}
