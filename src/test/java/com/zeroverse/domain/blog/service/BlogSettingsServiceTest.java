package com.zeroverse.domain.blog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.util.SlugGenerator;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.BlogResponse;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.InitialSetupRequest;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.InitialSetupResponse;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.PublicBlogResponse;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.UpdateBlogRequest;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * BlogSettingsService 테스트(FR-SETTINGS-03·04, FR-BLOG-01).
 *
 * <p>블로그 조회/수정, 초기 설정, 공개 조회를 검증한다. 동시 slug 변경과 동시 초기 설정은
 * ConcurrentSetupTest에서 실제 MySQL 동시 요청으로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlogSettingsServiceTest extends MySqlTestSupport {

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

    private Blog createBlog(User user, String title, String urlSlug) {
        Blog blog = Blog.createDefault(user, title, urlSlug);
        return blogRepository.save(blog);
    }

    @Nested
    @DisplayName("getBlog")
    class GetBlogTests {

        @Test
        @DisplayName("사용자의 블로그를 조회한다")
        void returnBlogForExistingUser() {
            User user = createUser("getblog@test.com", "getnick");
            Blog blog = createBlog(user, "제 블로그입니다", "getblog");

            BlogResponse response = blogSettingsService.getBlog(user.getId());

            assertThat(response)
                    .extracting("id", "title", "urlSlug", "isSetupCompleted")
                    .containsExactly(blog.getId(), "제 블로그입니다", "getblog", false);
        }

        @Test
        @DisplayName("없는 사용자는 USER_001을 던진다")
        void throwUser001ForNonexistentUser() {
            assertThatThrownBy(() -> blogSettingsService.getBlog(99999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_001);
        }

        @Test
        @DisplayName("soft delete된 사용자는 USER_001을 던진다")
        void throwUser001ForDeletedUser() {
            User user = createUser("deleted@test.com", "deletednick");
            createBlog(user, "제 블로그", "deletedblog");
            user.softDelete();
            userRepository.save(user);

            assertThatThrownBy(() -> blogSettingsService.getBlog(user.getId()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_001);
        }

        @Test
        @DisplayName("soft delete된 블로그는 BLOG_001을 던진다")
        void throwBlog001ForDeletedBlog() {
            User user = createUser("blogdeleted@test.com", "blogupdatenick");
            Blog blog = createBlog(user, "제 블로그", "blogupdateblog");
            blog.softDelete();
            blogRepository.save(blog);

            assertThatThrownBy(() -> blogSettingsService.getBlog(user.getId()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BLOG_001);
        }
    }

    @Nested
    @DisplayName("updateBlog")
    class UpdateBlogTests {

        @Test
        @DisplayName("블로그 정보를 수정한다")
        void updateBlogInfo() {
            User user = createUser("update@test.com", "updatenick");
            Blog blog = createBlog(user, "원래 제목", "original-slug");
            UpdateBlogRequest request = new UpdateBlogRequest(
                    "새로운 제목",
                    "new-slug",
                    "새로운 소개");

            BlogResponse response = blogSettingsService.updateBlog(user.getId(), request);

            assertThat(response)
                    .extracting("title", "urlSlug", "description")
                    .containsExactly("새로운 제목", "new-slug", "새로운 소개");

            Blog updated = blogRepository.findByIdAndDeletedAtIsNull(blog.getId()).get();
            assertThat(updated).extracting("title", "urlSlug", "description")
                    .containsExactly("새로운 제목", "new-slug", "새로운 소개");
        }

        @Test
        @DisplayName("자신의 기존 slug로 업데이트해도 중복이라 하지 않는다")
        void allowSelfExclusionSlug() {
            User user = createUser("selfslug@test.com", "selfslugNick");
            Blog blog = createBlog(user, "제 블로그", "self-slug");
            UpdateBlogRequest request = new UpdateBlogRequest(
                    "새 제목",
                    "self-slug",  // 기존 slug 그대로
                    null);

            BlogResponse response = blogSettingsService.updateBlog(user.getId(), request);

            assertThat(response.urlSlug()).isEqualTo("self-slug");
        }

        @Test
        @DisplayName("다른 블로그의 slug로 변경하면 BLOG_002를 던진다")
        void throwBlog002ForDuplicateSlug() {
            User user1 = createUser("dup@test.com", "dupnick1");
            User user2 = createUser("dup2@test.com", "dupnick2");
            createBlog(user1, "블로그1", "blog-1");
            Blog blog2 = createBlog(user2, "블로그2", "blog-2");

            UpdateBlogRequest request = new UpdateBlogRequest(
                    "새 제목",
                    "blog-1",  // 다른 사용자의 slug
                    null);

            assertThatThrownBy(() -> blogSettingsService.updateBlog(user2.getId(), request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BLOG_002);
        }

        @Test
        @DisplayName("예약어 slug는 BLOG_003을 던진다")
        void throwBlog003ForReservedSlug() {
            User user = createUser("reserved@test.com", "reservednick");
            createBlog(user, "제 블로그", "myblog");
            UpdateBlogRequest request = new UpdateBlogRequest(
                    "새 제목",
                    "admin",  // 예약어
                    null);

            assertThatThrownBy(() -> blogSettingsService.updateBlog(user.getId(), request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BLOG_003);
        }

        @Test
        @DisplayName("2자 slug는 BLOG_003을 던진다")
        void throwBlog003ForTooShortSlug() {
            User user = createUser("short@test.com", "shortnick");
            createBlog(user, "제 블로그", "myblog");
            UpdateBlogRequest request = new UpdateBlogRequest(
                    "새 제목",
                    "ab",  // 2자는 불가
                    null);

            assertThatThrownBy(() -> blogSettingsService.updateBlog(user.getId(), request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BLOG_003);
        }

        @Test
        @DisplayName("31자 slug는 BLOG_003을 던진다")
        void throwBlog003ForTooLongSlug() {
            User user = createUser("long@test.com", "longnick");
            createBlog(user, "제 블로그", "myblog");
            String longSlug = "a".repeat(31);
            UpdateBlogRequest request = new UpdateBlogRequest(
                    "새 제목",
                    longSlug,  // 31자는 불가
                    null);

            assertThatThrownBy(() -> blogSettingsService.updateBlog(user.getId(), request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BLOG_003);
        }

        @Test
        @DisplayName("대문자 포함 slug는 BLOG_003을 던진다")
        void throwBlog003ForUppercaseSlug() {
            User user = createUser("upper@test.com", "uppernick");
            createBlog(user, "제 블로그", "myblog");
            UpdateBlogRequest request = new UpdateBlogRequest(
                    "새 제목",
                    "MyBlog",  // 대문자 불가
                    null);

            assertThatThrownBy(() -> blogSettingsService.updateBlog(user.getId(), request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BLOG_003);
        }

        @Test
        @DisplayName("없는 사용자는 USER_001을 던진다")
        void throwUser001ForNonexistentUser() {
            UpdateBlogRequest request = new UpdateBlogRequest(
                    "새 제목",
                    "new-slug",
                    null);

            assertThatThrownBy(() -> blogSettingsService.updateBlog(99999L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_001);
        }
    }

    @Nested
    @DisplayName("initialSetup")
    class InitialSetupTests {

        @Test
        @DisplayName("제목과 slug를 설정하면 isSetupCompleted=true로 변경한다")
        void initialSetupCompletes() {
            User user = createUser("setup@test.com", "setupnick");
            Blog blog = createBlog(user, "기본 제목", "default-slug");
            assertThat(blog.getIsSetupCompleted()).isFalse();

            InitialSetupRequest request = new InitialSetupRequest(
                    "사용자가 설정한 제목",
                    "user-slug",
                    "사용자가 설정한 소개");

            InitialSetupResponse response = blogSettingsService.initialSetup(user.getId(), request);

            assertThat(response)
                    .extracting("title", "urlSlug", "description", "isSetupCompleted")
                    .containsExactly("사용자가 설정한 제목", "user-slug", "사용자가 설정한 소개", true);

            Blog updated = blogRepository.findByIdAndDeletedAtIsNull(blog.getId()).get();
            assertThat(updated.getIsSetupCompleted()).isTrue();
        }

        @Test
        @DisplayName("빈 title은 '{nickname}의 블로그'로 기본값이 된다")
        void emptyTitleUsesDefault() {
            User user = createUser("emptitle@test.com", "titledefaultnick");
            createBlog(user, "기본 제목", "default");
            InitialSetupRequest request = new InitialSetupRequest(
                    "",  // 빈 title
                    "user-slug",
                    null);

            InitialSetupResponse response = blogSettingsService.initialSetup(user.getId(), request);

            assertThat(response.title()).isEqualTo("titledefaultnick의 블로그");
        }

        @Test
        @DisplayName("null title은 '{nickname}의 블로그'로 기본값이 된다")
        void nullTitleUsesDefault() {
            User user = createUser("nulltitle@test.com", "titledefaultnick2");
            createBlog(user, "기본 제목", "default2");
            InitialSetupRequest request = new InitialSetupRequest(
                    null,  // null title
                    "user-slug",
                    null);

            InitialSetupResponse response = blogSettingsService.initialSetup(user.getId(), request);

            assertThat(response.title()).isEqualTo("titledefaultnick2의 블로그");
        }

        @Test
        @DisplayName("빈 slug는 nickname 기반으로 자동 생성된다")
        void emptySlugAutoGenerates() {
            User user = createUser("emptyslug@test.com", "auto-nick");
            createBlog(user, "기본 제목", "default-slug");
            InitialSetupRequest request = new InitialSetupRequest(
                    "제목",
                    "",  // 빈 slug
                    null);

            InitialSetupResponse response = blogSettingsService.initialSetup(user.getId(), request);

            assertThat(response.urlSlug()).isEqualTo("auto-nick");
        }

        /**
         * 위 테스트는 블로그 slug를 {@code default-slug}로 만들어 <b>실제 가입 상태를 재현하지
         * 않았다</b>. 진짜 가입은 nickname 기반 slug로 기본 블로그를 만들기 때문에(UserRegistrar),
         * 자동 할당이 자기 블로그를 제외하지 않으면 자기 slug를 충돌로 보고 {@code nick-2}로 민다.
         * 중복이 없는데 공개 URL이 바뀌는 것이라 실제 가입 상태를 그대로 세워 확인한다.
         */
        @Test
        @DisplayName("가입 때 받은 slug를 그대로 둔 채 slug를 비우면 그 slug를 유지한다")
        void emptySlugKeepsRegisteredSlug() {
            User user = createUser("keepslug@test.com", "keepnick");
            // 실제 가입과 동일하게 nickname 기반 slug로 기본 블로그를 만든다.
            String registered = SlugGenerator.fromNickname("keepnick");
            createBlog(user, "keepnick의 블로그", registered);

            InitialSetupResponse response = blogSettingsService.initialSetup(
                    user.getId(), new InitialSetupRequest("제목", "", null));

            assertThat(response.urlSlug())
                    .as("자기 slug를 충돌로 오인해 suffix를 붙이면 안 된다")
                    .isEqualTo(registered);
        }

        /**
         * 자기 제외가 "아무나 제외"로 번지면 안 된다. 다른 블로그가 이미 쥔 slug는 여전히 피해야 한다.
         *
         * <p>블로그 slug는 닉네임과 독립적으로 정할 수 있으므로, 남의 블로그가 내 후보 slug를
         * 선점한 상황을 그대로 세운다.
         */
        @Test
        @DisplayName("다른 블로그가 쓰는 slug는 자동 할당에서 피한다")
        void emptySlugAvoidsOtherBlogsSlug() {
            // 남의 블로그가 "latenick"을 선점한다.
            User squatter = createUser("squatter@test.com", "squatternick");
            createBlog(squatter, "선점", "latenick");

            // 후보 base가 "latenick"이 되는 사용자
            User late = createUser("late@test.com", "latenick");
            createBlog(late, "나중", "late-default");
            assertThat(SlugGenerator.fromNickname("latenick")).isEqualTo("latenick");

            InitialSetupResponse response = blogSettingsService.initialSetup(
                    late.getId(), new InitialSetupRequest("제목", "", null));

            assertThat(response.urlSlug())
                    .as("남이 쥔 slug를 가져가면 안 된다")
                    .isEqualTo("latenick-2");
        }

        @Test
        @DisplayName("이미 초기 설정이 완료되면 BLOG_004를 던진다")
        void throwBlog004IfAlreadySetup() {
            User user = createUser("alreadysetup@test.com", "setupnick");
            Blog blog = createBlog(user, "기본 제목", "default");
            blog.initialSetup("처음 설정", "first-setup", null);
            blogRepository.saveAndFlush(blog);

            InitialSetupRequest request = new InitialSetupRequest(
                    "다시 설정",
                    "second-setup",
                    null);

            assertThatThrownBy(() -> blogSettingsService.initialSetup(user.getId(), request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BLOG_004);
        }

        @Test
        @DisplayName("없는 사용자는 USER_001을 던진다")
        void throwUser001ForNonexistentUser() {
            InitialSetupRequest request = new InitialSetupRequest(
                    "제목",
                    "slug",
                    null);

            assertThatThrownBy(() -> blogSettingsService.initialSetup(99999L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_001);
        }
    }

    @Nested
    @DisplayName("getPublicBlog")
    class GetPublicBlogTests {

        @Test
        @DisplayName("공개 블로그를 조회하고 소유자 정보를 포함한다")
        void returnPublicBlogWithOwner() {
            User user = createUser("public@test.com", "publicnick");
            user.updateProfile("공개 사용자", "publicnick", "공개 소개", null, "http://example.com/pic.jpg");
            userRepository.save(user);

            Blog blog = createBlog(user, "공개 제목", "public-blog");

            PublicBlogResponse response = blogSettingsService.getPublicBlog("public-blog");

            assertThat(response)
                    .extracting("title", "urlSlug")
                    .containsExactly("공개 제목", "public-blog");
            assertThat(response.owner())
                    .extracting("nickname", "bio", "profileImageUrl")
                    .containsExactly("publicnick", "공개 소개", "http://example.com/pic.jpg");

            // 실명은 공개 응답에 담지 않는다(OwnerInfo 참조).
            assertThat(user.getName()).isEqualTo("공개 사용자");
            assertThat(response.owner().toString()).doesNotContain("공개 사용자");
        }

        @Test
        @DisplayName("없는 slug는 BLOG_001을 던진다")
        void throwBlog001ForNonexistentSlug() {
            assertThatThrownBy(() -> blogSettingsService.getPublicBlog("nonexistent"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BLOG_001);
        }

        @Test
        @DisplayName("soft delete된 블로그는 BLOG_001을 던진다")
        void throwBlog001ForDeletedBlog() {
            User user = createUser("deleted@test.com", "deletednick");
            Blog blog = createBlog(user, "삭제 블로그", "deleted-blog");
            blog.softDelete();
            blogRepository.save(blog);

            assertThatThrownBy(() -> blogSettingsService.getPublicBlog("deleted-blog"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BLOG_001);
        }

        @Test
        @DisplayName("soft delete된 소유자의 블로그는 BLOG_001을 던진다")
        void throwBlog001IfOwnerDeleted() {
            User user = createUser("deletedowner@test.com", "deletedownernick");
            Blog blog = createBlog(user, "블로그", "blog");
            user.softDelete();
            userRepository.save(user);

            assertThatThrownBy(() -> blogSettingsService.getPublicBlog("blog"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BLOG_001);
        }
    }
}
