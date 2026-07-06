package com.zeroverse.domain.post.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryViewCountGuard 테스트")
class InMemoryViewCountGuardTest {

    private InMemoryViewCountGuard guard;
    private static final Long POST_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final String IP_HASH = "hash123";

    @BeforeEach
    void setUp() {
        guard = new InMemoryViewCountGuard();
    }

    @Test
    @DisplayName("첫 조회는 증가 허용한다")
    void testFirstViewIncremented() {
        boolean canIncrement = guard.canIncrement(POST_ID, USER_ID, null);

        assertThat(canIncrement).isTrue();
    }

    @Test
    @DisplayName("24시간 내 재조회는 증가 차단한다")
    void testSecondViewWithin24hBlocked() {
        guard.canIncrement(POST_ID, USER_ID, null);

        boolean canIncrement = guard.canIncrement(POST_ID, USER_ID, null);

        assertThat(canIncrement).isFalse();
    }

    @Test
    @DisplayName("다른 사용자의 조회는 독립적으로 증가한다")
    void testDifferentUserIndependent() {
        guard.canIncrement(POST_ID, USER_ID, null);

        boolean canIncrement = guard.canIncrement(POST_ID, 999L, null);

        assertThat(canIncrement).isTrue();
    }

    @Test
    @DisplayName("비로그인 사용자의 조회는 IP 해시로 구분한다")
    void testAnonymousUserWithIpHash() {
        guard.canIncrement(POST_ID, null, IP_HASH);

        boolean canIncrement = guard.canIncrement(POST_ID, null, IP_HASH);

        assertThat(canIncrement).isFalse();
    }

    @Test
    @DisplayName("다른 IP 해시는 독립적으로 증가한다")
    void testDifferentIpHashIndependent() {
        guard.canIncrement(POST_ID, null, IP_HASH);

        boolean canIncrement = guard.canIncrement(POST_ID, null, "differentHash");

        assertThat(canIncrement).isTrue();
    }

    @Test
    @DisplayName("다른 게시글의 조회는 독립적으로 증가한다")
    void testDifferentPostIndependent() {
        guard.canIncrement(POST_ID, USER_ID, null);

        boolean canIncrement = guard.canIncrement(999L, USER_ID, null);

        assertThat(canIncrement).isTrue();
    }

    @Test
    @DisplayName("cleanup 메서드는 정상 작동한다")
    void testCleanup() {
        guard.canIncrement(POST_ID, USER_ID, null);

        // cleanup 실행 (정상 작동 확인만)
        guard.cleanup();

        // cleanup 후에도 기본 동작은 유지되어야 함
        assertThat(guard.canIncrement(POST_ID, USER_ID, null)).isFalse();
    }
}
