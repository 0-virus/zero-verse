package com.zeroverse.domain.blog.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.user.entity.User;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Blog 엔티티 단위 테스트(FR-SETTINGS-03, FR-SETTINGS-04).
 *
 * <p>블로그 정보 변경과 초기 설정의 불변식을 검증한다. 검증 실패는 BusinessException을
 * 던지며, slug 검증 실패는 BLOG_003(400), 기타 필드는 VALIDATION_001(400),
 * 초기설정 중복은 BLOG_004(409)를 반환한다.
 */
@DisplayName("Blog 엔티티")
class BlogTest {

    private User createTestUser() {
        return User.register("test@example.com", "$2a$12$hashedPassword", "테스트", "testuser",
                            LocalDate.of(1990, 1, 1));
    }

    private Blog createTestBlog(User user) {
        return Blog.createDefault(user, "testuser의 블로그", "testuser");
    }

    @Nested
    @DisplayName("updateInfo 메서드")
    class UpdateInfoTests {

        @Test
        @DisplayName("모든 필드를 변경할 수 있다")
        void updateAllFields() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            blog.updateInfo("새 제목", "newslug", "새로운 소개");

            assertThat(blog.getTitle()).isEqualTo("새 제목");
            assertThat(blog.getUrlSlug()).isEqualTo("newslug");
            assertThat(blog.getDescription()).isEqualTo("새로운 소개");
        }

        @Test
        @DisplayName("description을 null로 설정할 수 있다")
        void updateWithNullDescription() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);
            blog.updateInfo("title", "slug", "소개");
            assertThat(blog.getDescription()).isEqualTo("소개");

            blog.updateInfo("title", "slug", null);

            assertThat(blog.getDescription()).isNull();
        }

        // --- title 검증 ---

        @Test
        @DisplayName("title이 null이면 BusinessException(VALIDATION_001)을 던진다")
        void titleNullThrows() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThatThrownBy(() -> blog.updateInfo(null, "slug", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_001));
        }

        @Test
        @DisplayName("title이 공백이면 BusinessException(VALIDATION_001)을 던진다")
        void titleBlankThrows() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThatThrownBy(() -> blog.updateInfo("   ", "slug", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_001));
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 100, 200})
        @DisplayName("title이 1~200자일 때 성공한다")
        void titleWithinBoundary(int length) {
            User user = createTestUser();
            Blog blog = createTestBlog(user);
            String title = "t".repeat(length);

            blog.updateInfo(title, "valid-slug", null);

            assertThat(blog.getTitle()).isEqualTo(title);
        }

        @Test
        @DisplayName("title이 201자이면 BusinessException(VALIDATION_001)을 던진다")
        void titleExceedsMaxLength() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);
            String tooLongTitle = "t".repeat(201);

            assertThatThrownBy(() -> blog.updateInfo(tooLongTitle, "slug", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_001));
        }

        // --- urlSlug 검증: 포맷 ---

        @Test
        @DisplayName("urlSlug이 null이면 BusinessException(BLOG_003)을 던진다")
        void slugNullThrows() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThatThrownBy(() -> blog.updateInfo("title", null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_003));
        }

        @Test
        @DisplayName("urlSlug이 공백이면 BusinessException(BLOG_003)을 던진다")
        void slugBlankThrows() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThatThrownBy(() -> blog.updateInfo("title", "   ", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_003));
        }

        // --- urlSlug 검증: 길이 (3~30) ---

        @Test
        @DisplayName("urlSlug이 2자이면 BusinessException(BLOG_003)을 던진다 (최소 3자)")
        void slugBelowMinLength() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThatThrownBy(() -> blog.updateInfo("title", "ab", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_003));
        }

        @ParameterizedTest
        @ValueSource(ints = {3, 30})
        @DisplayName("urlSlug이 3~30자일 때 성공한다")
        void slugValidLength(int length) {
            User user = createTestUser();
            Blog blog = createTestBlog(user);
            String slug = "a".repeat(length);

            blog.updateInfo("title", slug, null);

            assertThat(blog.getUrlSlug()).isEqualTo(slug);
        }

        @Test
        @DisplayName("urlSlug이 31자이면 BusinessException(BLOG_003)을 던진다 (최대 30자)")
        void slugExceedsMaxLength() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);
            String tooLongSlug = "a".repeat(31);

            assertThatThrownBy(() -> blog.updateInfo("title", tooLongSlug, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_003));
        }

        // --- urlSlug 검증: 형식 (소문자, 숫자, 하이픈) ---

        @Test
        @DisplayName("urlSlug에 대문자가 있으면 BusinessException(BLOG_003)을 던진다")
        void slugWithUppercaseThrows() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThatThrownBy(() -> blog.updateInfo("title", "My-Slug", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_003));
        }

        @Test
        @DisplayName("urlSlug에 언더스코어가 있으면 BusinessException(BLOG_003)을 던진다")
        void slugWithUnderscoreThrows() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThatThrownBy(() -> blog.updateInfo("title", "my_slug", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_003));
        }

        @Test
        @DisplayName("urlSlug에 앞 하이픈이 있으면 BusinessException(BLOG_003)을 던진다")
        void slugWithLeadingHyphenThrows() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThatThrownBy(() -> blog.updateInfo("title", "-myslug", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_003));
        }

        @Test
        @DisplayName("urlSlug에 뒤 하이픈이 있으면 BusinessException(BLOG_003)을 던진다")
        void slugWithTrailingHyphenThrows() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThatThrownBy(() -> blog.updateInfo("title", "myslug-", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_003));
        }

        @Test
        @DisplayName("urlSlug에 연속 하이픈이 있으면 BusinessException(BLOG_003)을 던진다")
        void slugWithDoubleHyphenThrows() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThatThrownBy(() -> blog.updateInfo("title", "my--slug", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_003));
        }

        @Test
        @DisplayName("유효한 slug: 소문자, 숫자, 하이픈 혼합")
        void validSlugWithMixedCharacters() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            blog.updateInfo("title", "my-awesome-blog123", null);

            assertThat(blog.getUrlSlug()).isEqualTo("my-awesome-blog123");
        }

        // --- urlSlug 검증: 예약어 ---

        @ParameterizedTest
        @ValueSource(strings = {"admin", "api", "signin", "signup", "settings", "write",
                               "edit", "search"})
        @DisplayName("예약어 slug는 BusinessException(BLOG_003)을 던진다")
        void reservedSlugThrows(String reserved) {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThatThrownBy(() -> blog.updateInfo("title", reserved, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_003));
        }

        @Test
        @DisplayName("자신의 기존 slug로 변경할 수 있다 (유니크 제약은 service에서 검증)")
        void canUpdateToExistingSlug() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);
            String originalSlug = blog.getUrlSlug();

            blog.updateInfo("new-title", originalSlug, "new-desc");

            assertThat(blog.getUrlSlug()).isEqualTo(originalSlug);
        }
    }

    @Nested
    @DisplayName("initialSetup 메서드")
    class InitialSetupTests {

        @Test
        @DisplayName("초기 설정을 완료한다")
        void setupBlogSuccessfully() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThat(blog.getIsSetupCompleted()).isFalse();

            blog.initialSetup("내 블로그", "my-slug", null);

            assertThat(blog.getTitle()).isEqualTo("내 블로그");
            assertThat(blog.getUrlSlug()).isEqualTo("my-slug");
            assertThat(blog.getDescription()).isNull();
            assertThat(blog.getIsSetupCompleted()).isTrue();
        }

        /**
         * 초기 설정 화면은 `한 줄 소개`까지 함께 받는다(REQUIREMENTS FR-SETTINGS-04 "필드: title,
         * url_slug, description", DESIGN-SYSTEM §8.6). 이 인자를 무시하고 저장하지 않으면
         * 사용자가 입력한 소개가 소리 없이 사라진다.
         */
        @Test
        @DisplayName("초기 설정에서 받은 한 줄 소개를 저장한다")
        void setupStoresDescription() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            blog.initialSetup("내 블로그", "my-slug", "우주를 항해하는 기록");

            assertThat(blog.getDescription()).isEqualTo("우주를 항해하는 기록");
            assertThat(blog.getIsSetupCompleted()).isTrue();
        }

        @Test
        @DisplayName("이미 완료된 setup을 다시 호출하면 BusinessException(BLOG_004)을 던진다")
        void alreadyCompletedSetupThrows() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            blog.initialSetup("title-1", "slug-1", null);
            assertThat(blog.getIsSetupCompleted()).isTrue();

            assertThatThrownBy(() -> blog.initialSetup("title-2", "slug-2", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_004));
        }

        @Test
        @DisplayName("setup 중에 title이 null이면 BusinessException(VALIDATION_001)을 던진다")
        void setupWithNullTitleThrows() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThatThrownBy(() -> blog.initialSetup(null, "valid-slug", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_001));
        }

        @Test
        @DisplayName("setup 중에 slug가 null이면 BusinessException(BLOG_003)을 던진다")
        void setupWithNullSlugThrows() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThatThrownBy(() -> blog.initialSetup("Valid Title", null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_003));
        }

        @Test
        @DisplayName("setup 중에 slug가 예약어이면 BusinessException(BLOG_003)을 던진다")
        void setupWithReservedSlugThrows() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThatThrownBy(() -> blog.initialSetup("My Blog", "admin", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_003));
        }

        @Test
        @DisplayName("setup 중에 slug가 형식을 벗어나면 BusinessException(BLOG_003)을 던진다")
        void setupWithInvalidSlugFormatThrows() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThatThrownBy(() -> blog.initialSetup("My Blog", "invalid slug", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_003));
        }

        @ParameterizedTest
        @ValueSource(ints = {3, 30})
        @DisplayName("setup 중에 slug가 3~30자일 때 성공한다")
        void setupWithValidSlugLength(int length) {
            User user = createTestUser();
            Blog blog = createTestBlog(user);
            String slug = "a".repeat(length);

            blog.initialSetup("Title", slug, null);

            assertThat(blog.getUrlSlug()).isEqualTo(slug);
            assertThat(blog.getIsSetupCompleted()).isTrue();
        }

        @Test
        @DisplayName("setup 중에 slug가 31자이면 BusinessException(BLOG_003)을 던진다")
        void setupWithInvalidSlugLengthThrows() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);
            String tooLongSlug = "a".repeat(31);

            assertThatThrownBy(() -> blog.initialSetup("Title", tooLongSlug, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_003));
        }
    }

    @Nested
    @DisplayName("Mutation 테스트 — initialSetup 상태 전이와 조건 검사")
    class InitialSetupMutationTests {

        @Test
        @DisplayName("isSetupCompleted 상태 전이 생략 시 실패한다 (재호출 시 BLOG_004 던져야 함)")
        void mutationDetectStateTransitionSkip() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            blog.initialSetup("title-1", "slug-1", null);
            assertThat(blog.getIsSetupCompleted()).isTrue();

            // 두 번째 호출은 반드시 BLOG_004를 던져야 한다
            assertThatThrownBy(() -> blog.initialSetup("title-2", "slug-2", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_004));
        }

        @Test
        @DisplayName("조건 검사 생략 시 실패한다 (완료된 setup 재호출 시 BLOG_004 거부)")
        void mutationDetectConditionCheckSkip() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            blog.initialSetup("title-1", "slug-1", null);

            // 반드시 BLOG_004를 던져야 한다
            assertThatThrownBy(() -> blog.initialSetup("title-2", "slug-2", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_004));
        }
    }

    @Nested
    @DisplayName("Mutation 테스트 — updateInfo slug 검증")
    class UpdateInfoMutationTests {

        @Test
        @DisplayName("slug 형식 검증 생략 시 실패한다 (예약어 'admin' 거부되어야 함)")
        void mutationDetectSlugFormatValidationSkip() {
            User user = createTestUser();
            Blog blog = createTestBlog(user);

            assertThatThrownBy(() -> blog.updateInfo("title", "admin", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BLOG_003));
        }
    }
}
