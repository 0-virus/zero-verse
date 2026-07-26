package com.zeroverse.domain.blog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.InitialSetupRequest;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.UpdateBlogRequest;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
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
 * 동시 블로그 설정 테스트(FR-SETTINGS-03·04).
 *
 * <p>두 사용자가 동시에 같은 slug로 변경하거나, 하나의 사용자가 초기 설정을 동시에 호출했을 때
 * 정확히 1회만 성공하고 나머지는 적절한 오류를 반환하는지 검증한다.
 *
 * <p>{@code @Transactional}을 붙이지 않는다 — 붙이면 스레드가 트랜잭션을 공유해 동시성이
 * 재현되지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrentSetupTest extends MySqlTestSupport {

    @Autowired
    private BlogSettingsService blogSettingsService;

    @Autowired
    private BlogRepository blogRepository;

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

    private Blog createBlog(User user, String title, String slug) {
        Blog blog = Blog.createDefault(user, title, slug);
        return blogRepository.save(blog);
    }

    @Test
    @DisplayName("같은 slug로 동시 변경하면 하나만 성공하고 나머지는 BLOG_002다")
    void concurrentSlugChangeYieldsConflict() throws Exception {
        User user1 = createUser("sluguser1@test.com", "slugnick1");
        User user2 = createUser("sluguser2@test.com", "slugnick2");
        createBlog(user1, "블로그1", "blog-1");
        createBlog(user2, "블로그2", "blog-2");

        List<Result> results = updateSlugConcurrently(
                user1.getId(), "shared-slug",
                user2.getId(), "shared-slug");

        long successes = results.stream().filter(r -> r.isSuccess()).count();
        assertThat(successes)
                .as("같은 slug 동시 변경은 하나만 성공해야 한다")
                .isEqualTo(1);

        List<String> errorCodes = results.stream()
                .filter(r -> !r.isSuccess())
                .map(r -> r.errorCode())
                .toList();
        assertThat(errorCodes)
                .as("실패는 BLOG_002(slug 중복)여야 한다")
                .containsOnly("BLOG_002");

        // 한 블로그의 slug만 변경되었는지 확인
        Blog blog1 = blogRepository.findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(user1.getId()).get();
        Blog blog2 = blogRepository.findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(user2.getId()).get();
        long changedCount = (blog1.getUrlSlug().equals("shared-slug") ? 1 : 0)
                + (blog2.getUrlSlug().equals("shared-slug") ? 1 : 0);
        assertThat(changedCount)
                .as("정확히 한 블로그만 slug가 변경되어야 한다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("같은 사용자의 초기 설정을 동시에 호출하면 하나만 성공한다")
    void concurrentInitialSetupYieldsConflict() throws Exception {
        User user = createUser("setupuser@test.com", "setupnick");
        createBlog(user, "기본 제목", "default-slug");

        List<Result> results = setupConcurrently(
                user.getId(), "제목1", "slug-1",
                user.getId(), "제목2", "slug-2");

        long successes = results.stream().filter(r -> r.isSuccess()).count();
        assertThat(successes)
                .as("초기 설정은 정확히 1회만 성공해야 한다")
                .isEqualTo(1);

        // 실패 사유까지 확인한다. "하나만 성공"만 보면 실패가 slug 충돌(BLOG_002)이나
        // 서버 오류(COMMON_500)여도 통과해 버린다 — 계약은 BLOG_004(409)다.
        List<String> errorCodes = results.stream()
                .filter(r -> !r.isSuccess())
                .map(r -> r.errorCode())
                .toList();
        assertThat(errorCodes)
                .as("이미 완료된 초기 설정의 재호출은 BLOG_004여야 한다")
                .containsOnly("BLOG_004");

        // 블로그 상태 확인: 정확히 1회만 완료된 상태여야 한다
        Blog blog = blogRepository.findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(user.getId()).get();
        assertThat(blog.getIsSetupCompleted())
                .as("정확히 1회의 성공으로 초기 설정이 완료되어야 한다")
                .isTrue();
    }

    // --- helpers ---

    private record Result(boolean isSuccess, String errorCode) {}

    private List<Result> updateSlugConcurrently(Long userId1, String slug1, Long userId2, String slug2)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Result>> futures = new ArrayList<>();

        futures.add(pool.submit(() -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                UpdateBlogRequest request = new UpdateBlogRequest("제목", slug1, null);
                blogSettingsService.updateBlog(userId1, request);
                return new Result(true, null);
            } catch (BusinessException e) {
                return new Result(false, e.getErrorCode().getCode());
            }
        }));

        futures.add(pool.submit(() -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                UpdateBlogRequest request = new UpdateBlogRequest("제목", slug2, null);
                blogSettingsService.updateBlog(userId2, request);
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

    private List<Result> setupConcurrently(
            Long userId1, String title1, String slug1,
            Long userId2, String title2, String slug2)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Result>> futures = new ArrayList<>();

        futures.add(pool.submit(() -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                InitialSetupRequest request = new InitialSetupRequest(title1, slug1, null);
                blogSettingsService.initialSetup(userId1, request);
                return new Result(true, null);
            } catch (BusinessException e) {
                return new Result(false, e.getErrorCode().getCode());
            }
        }));

        futures.add(pool.submit(() -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                InitialSetupRequest request = new InitialSetupRequest(title2, slug2, null);
                blogSettingsService.initialSetup(userId2, request);
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
