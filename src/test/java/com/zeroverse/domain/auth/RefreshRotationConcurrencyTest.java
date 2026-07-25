package com.zeroverse.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.auth.repository.RefreshTokenRepository;
import com.zeroverse.domain.auth.service.RefreshTokenService;
import com.zeroverse.domain.auth.service.RefreshTokenService.IssuedPair;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Refresh rotation 동시성 — 심의 필수 변경(ADR-0003 §2).
 *
 * <p><b>mock이 아니라 실제 MySQL의 독립 트랜잭션 두 개</b>로 검증한다. 같은 refresh 토큰으로
 * 동시에 갱신하면 {@code PESSIMISTIC_WRITE} 잠금 덕분에 하나만 성공해야 한다. 잠금이 빠지면
 * 두 요청이 각자 새 세션을 만들어 세션이 갈라진다 — 이 테스트가 그 회귀를 잡는다.
 *
 * <p>테스트 클래스에 {@code @Transactional}을 붙이지 않는다. 붙이면 두 스레드가 같은
 * 트랜잭션을 공유해 동시성 자체가 재현되지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RefreshRotationConcurrencyTest extends MySqlTestSupport {

    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("같은 refresh로 동시에 갱신하면 하나만 성공하고 나머지는 AUTH_003이다")
    void concurrentRotationAllowsOnlyOneWinner() throws Exception {
        User user = persistUser("concurrent@zeroverse.test", "concurrentuser");
        String refreshToken = refreshTokenService.issue(user, Instant.now()).refresh().token();

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<IssuedPair>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return refreshTokenService.rotate(refreshToken, Instant.now());
            });
        }

        List<Future<IssuedPair>> futures = new ArrayList<>();
        for (Callable<IssuedPair> task : tasks) {
            futures.add(pool.submit(task));
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        AtomicInteger success = new AtomicInteger();
        AtomicInteger auth003 = new AtomicInteger();
        List<Throwable> unexpected = new ArrayList<>();

        for (Future<IssuedPair> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
                success.incrementAndGet();
            } catch (Exception e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (cause instanceof BusinessException be
                        && be.getErrorCode() == ErrorCode.AUTH_003) {
                    auth003.incrementAndGet();
                } else {
                    unexpected.add(cause);
                }
            }
        }
        pool.shutdownNow();

        assertThat(unexpected)
                .as("AUTH_003 외의 예외는 없어야 한다 (lock timeout 등)")
                .isEmpty();
        assertThat(success.get()).as("성공한 갱신 수").isEqualTo(1);
        assertThat(auth003.get()).as("AUTH_003으로 거부된 수").isEqualTo(threads - 1);
    }

    @Test
    @DisplayName("동시 갱신 후 활성 refresh row는 정확히 하나다")
    void exactlyOneActiveTokenRemainsAfterConcurrentRotation() throws Exception {
        User user = persistUser("single@zeroverse.test", "singleuser");
        String refreshToken = refreshTokenService.issue(user, Instant.now()).refresh().token();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return refreshTokenService.rotate(refreshToken, Instant.now());
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // 한 건은 AUTH_003으로 실패하는 것이 정상이다.
            }
        }
        pool.shutdownNow();

        long active = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUser().getId().equals(user.getId()))
                .filter(token -> !token.isRevoked())
                .count();

        assertThat(active).as("활성 refresh row 수").isEqualTo(1);
    }

    private User persistUser(String email, String nickname) {
        return userRepository.saveAndFlush(User.register(
                email,
                passwordEncoder.encode("Password123!"),
                "테스터",
                nickname,
                LocalDate.of(1995, 1, 1)));
    }
}
