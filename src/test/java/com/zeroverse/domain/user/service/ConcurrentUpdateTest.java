package com.zeroverse.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.user.dto.UserSettingsDtos.ChangePasswordRequest;
import com.zeroverse.domain.user.dto.UserSettingsDtos.UpdateProfileRequest;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    /**
     * 같은 사용자의 프로필 수정과 비밀번호 변경이 겹쳐도 서로를 지우지 않아야 한다.
     *
     * <p>두 요청은 각각 User를 조회해 자기 필드만 바꾸고 독립적으로 flush한다. Hibernate 기본
     * UPDATE는 <b>모든 컬럼</b>을 쓰므로, 둘이 옛 행을 함께 읽으면 나중 flush가 상대의 변경을
     * 자기가 읽은 낡은 값으로 덮는다 — 비밀번호를 바꾸고 성공 응답까지 받았는데 동시에 저장된
     * 프로필이 옛 해시를 되돌려 놓는다. {@code @DynamicUpdate}로 변경된 컬럼만 쓰면 두 요청이
     * 겹치지 않는다.
     *
     * <p>화면에서 프로필 카드와 비밀번호 카드는 각각 독립된 저장 버튼을 가지므로 실제로 동시
     * 제출이 가능한 경로다.
     */
    @Test
    @DisplayName("프로필 수정과 비밀번호 변경이 동시에 일어나도 서로를 덮어쓰지 않는다")
    void concurrentProfileAndPasswordKeepBothChanges() throws Exception {
        User user = createUser("both@test.com", "bothnick");
        String originalHash = user.getPassword();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Result>> futures = new ArrayList<>();

        futures.add(pool.submit(() -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                userSettingsService.updateProfile(
                        user.getId(),
                        new UpdateProfileRequest("바뀐이름", "bothnick", null, null, null));
                return new Result(true, null);
            } catch (BusinessException e) {
                return new Result(false, e.getErrorCode().getCode());
            }
        }));

        futures.add(pool.submit(() -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                userSettingsService.changePassword(
                        user.getId(),
                        new ChangePasswordRequest("password123!@", "NewPassw0rd!"));
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

        assertThat(results)
                .as("두 요청 모두 성공해야 한다 — 서로 다른 필드를 만진다")
                .allMatch(Result::isSuccess);

        User reloaded = userRepository.findByIdAndDeletedAtIsNull(user.getId()).orElseThrow();
        assertThat(reloaded.getName())
                .as("프로필 변경이 비밀번호 변경에 덮이면 안 된다")
                .isEqualTo("바뀐이름");
        assertThat(reloaded.getPassword())
                .as("비밀번호 변경이 프로필 변경에 덮이면 안 된다 — 성공 응답을 받은 변경이다")
                .isNotEqualTo(originalHash);
        assertThat(passwordEncoder.matches("NewPassw0rd!", reloaded.getPassword()))
                .as("새 비밀번호로 로그인할 수 있어야 한다")
                .isTrue();
    }

    /**
     * 위 서비스 레벨 테스트는 두 작업을 서비스 <b>진입 전</b>에만 동시에 풀어 준다. 한쪽이 SELECT와
     * 커밋을 모두 끝낸 뒤 다른 쪽이 읽으면 stale read가 생기지 않아 매핑이 잘못돼도 통과할 수 있다.
     *
     * <p>여기서는 **두 트랜잭션이 모두 행을 읽은 뒤에야** 쓰도록 barrier를 걸어 lost update 조건을
     * 결정적으로 만든다. {@code @DynamicUpdate}가 없으면 나중 커밋이 상대 컬럼을 낡은 값으로 덮는다.
     */
    @Test
    @DisplayName("두 트랜잭션이 같은 행을 읽은 뒤 각자 다른 컬럼을 써도 둘 다 남는다")
    void staleReadThenWriteKeepsBothColumns() throws Exception {
        User user = createUser("stale@test.com", "stalenick");
        Long id = user.getId();
        String originalHash = user.getPassword();
        String newHash = passwordEncoder.encode("NewPassw0rd!");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        CyclicBarrier bothRead = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<?> nameWriter = pool.submit(() -> tx.execute(status -> {
            User loaded = userRepository.findByIdAndDeletedAtIsNull(id).orElseThrow();
            awaitBarrier(bothRead); // 상대도 옛 행을 읽을 때까지 기다린다
            loaded.updateProfile("바뀐이름", "stalenick", null, null, null);
            userRepository.saveAndFlush(loaded);
            return null;
        }));

        Future<?> passwordWriter = pool.submit(() -> tx.execute(status -> {
            User loaded = userRepository.findByIdAndDeletedAtIsNull(id).orElseThrow();
            awaitBarrier(bothRead);
            loaded.changePassword(newHash);
            userRepository.saveAndFlush(loaded);
            return null;
        }));

        nameWriter.get(30, TimeUnit.SECONDS);
        passwordWriter.get(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        User reloaded = userRepository.findByIdAndDeletedAtIsNull(id).orElseThrow();
        assertThat(reloaded.getName())
                .as("이름 변경이 비밀번호 쓰기에 덮이면 안 된다")
                .isEqualTo("바뀐이름");
        assertThat(reloaded.getPassword())
                .as("비밀번호 변경이 이름 쓰기에 덮이면 안 된다")
                .isNotEqualTo(originalHash);
    }

    /**
     * `Blog`도 같은 위험이 있다. `updateBlog`은 잠금 없이, `initialSetup`은 잠금으로 같은 행을 쓴다.
     * 전체 컬럼 UPDATE라면 초기 설정과 겹친 수정이 {@code is_setup_completed}를 false로 되돌려
     * 설정을 끝낸 사용자가 다시 `/blog/setup`으로 끌려간다.
     */
    @Test
    @DisplayName("초기 설정과 겹친 블로그 수정이 완료 플래그를 되돌리지 않는다")
    void staleBlogUpdateDoesNotRevertSetupFlag() throws Exception {
        User user = createUser("blogstale@test.com", "blogstalenick");
        Blog blog = blogRepository.saveAndFlush(
                Blog.createDefault(user, "기본 제목", "blogstale-slug"));
        Long blogId = blog.getId();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        CyclicBarrier bothRead = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // 둘 다 is_setup_completed=false인 옛 행을 읽는다.
        Future<?> setupWriter = pool.submit(() -> tx.execute(status -> {
            Blog loaded = blogRepository.findByIdAndDeletedAtIsNull(blogId).orElseThrow();
            awaitBarrier(bothRead);
            loaded.initialSetup("설정한 제목", "blogstale-slug", null);
            blogRepository.saveAndFlush(loaded);
            return null;
        }));

        Future<?> descriptionWriter = pool.submit(() -> tx.execute(status -> {
            Blog loaded = blogRepository.findByIdAndDeletedAtIsNull(blogId).orElseThrow();
            awaitBarrier(bothRead);
            loaded.updateInfo(loaded.getTitle(), loaded.getUrlSlug(), "나중에 쓴 소개");
            blogRepository.saveAndFlush(loaded);
            return null;
        }));

        setupWriter.get(30, TimeUnit.SECONDS);
        descriptionWriter.get(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        Blog reloaded = blogRepository.findByIdAndDeletedAtIsNull(blogId).orElseThrow();
        assertThat(reloaded.getIsSetupCompleted())
                .as("완료 플래그가 겹친 수정에 false로 되돌려지면 안 된다")
                .isTrue();
        assertThat(reloaded.getDescription())
                .as("소개 변경도 남아야 한다")
                .isEqualTo("나중에 쓴 소개");
    }

    // --- helpers ---

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("barrier 대기 실패", e);
        }
    }

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
