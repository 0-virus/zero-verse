package com.zeroverse.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.user.dto.UserSettingsDtos.UpdateProfileRequest;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * 동시 프로필 업데이트 테스트(FR-SETTINGS-01).
 *
 * <p>두 사용자가 동시에 같은 nickname으로 변경하면 선조회를 둘 다 통과하고 DB unique 제약이
 * 하나를 거부한다. 그 예외가 USER_002로 매핑되는지 검증한다.
 *
 * <p>{@code @Transactional}을 붙이지 않는다 — 붙이면 스레드가 트랜잭션을 공유해 동시성이
 * 재현되지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrentUpdateTest extends MySqlTestSupport {

    @Autowired
    private UserSettingsService userSettingsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User createUser(String email, String nickname) {
        User user = User.register(
                email,
                passwordEncoder.encode("password123!@"),
                "테스터",
                nickname,
                LocalDate.of(1990, 1, 1));
        return userRepository.save(user);
    }

    @Test
    @DisplayName("같은 nickname으로 동시 변경하면 하나만 성공하고 나머지는 USER_002다")
    void concurrentNicknameChangeYieldsUser002() throws Exception {
        User user1 = createUser("user1@test.com", "nick1");
        User user2 = createUser("user2@test.com", "nick2");

        List<Result> results = updateConcurrently(
                user1.getId(), "nick1", "shared-nick",
                user2.getId(), "nick2", "shared-nick");

        long successes = results.stream().filter(r -> r.isSuccess()).count();
        assertThat(successes)
                .as("같은 nickname 동시 변경은 하나만 성공해야 한다")
                .isEqualTo(1);

        List<String> errorCodes = results.stream()
                .filter(r -> !r.isSuccess())
                .map(r -> r.errorCode())
                .toList();
        assertThat(errorCodes)
                .as("실패는 모두 USER_002(nickname 중복)여야 한다")
                .containsOnly("USER_002");

        // 한 사용자의 nickname이 공유 nickname으로 변경되었는지 확인
        User updated1 = userRepository.findByIdAndDeletedAtIsNull(user1.getId()).get();
        User updated2 = userRepository.findByIdAndDeletedAtIsNull(user2.getId()).get();
        long changedCount = (updated1.getNickname().equals("shared-nick") ? 1 : 0)
                + (updated2.getNickname().equals("shared-nick") ? 1 : 0);
        assertThat(changedCount)
                .as("정확히 한 사용자만 nickname이 변경되어야 한다")
                .isEqualTo(1);
    }

    // --- helpers ---

    private record Result(boolean isSuccess, String errorCode) {}

    private List<Result> updateConcurrently(
            Long userId1, String oldNick1, String newNick,
            Long userId2, String oldNick2, String newNick2)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Result>> futures = new ArrayList<>();

        futures.add(pool.submit(() -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                UpdateProfileRequest request = new UpdateProfileRequest(
                        "테스터", newNick, null, null, null);
                userSettingsService.updateProfile(userId1, request);
                return new Result(true, null);
            } catch (BusinessException e) {
                return new Result(false, e.getErrorCode().getCode());
            }
        }));

        futures.add(pool.submit(() -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                UpdateProfileRequest request = new UpdateProfileRequest(
                        "테스터", newNick2, null, null, null);
                userSettingsService.updateProfile(userId2, request);
                return new Result(true, null);
            } catch (BusinessException e) {
                return new Result(false, e.getErrorCode().getCode());
            }
        }));

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<Result> results = new ArrayList<>();
        for (Future<Result> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        return results;
    }
}
