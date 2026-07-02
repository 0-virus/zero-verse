package com.zeroverse.domain.blog.service;

import com.zeroverse.auth.dto.RegisterRequest;
import com.zeroverse.auth.service.AuthService;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.dto.blog.BlogInitialSetupRequest;
import com.zeroverse.dto.blog.BlogPublicResponse;
import com.zeroverse.dto.blog.BlogSettingsRequest;
import com.zeroverse.dto.blog.BlogSettingsResponse;
import com.zeroverse.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestBlogServiceConfig.class, TestAuthServiceConfig.class})
public class BlogServiceTest extends IntegrationTestSupport {

    @Autowired
    private BlogService blogService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private User testUser;
    private Blog testBlog;

    @BeforeEach
    void setUp() {
        // Clear repositories
        categoryRepository.deleteAllInBatch();
        blogRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // Create test user with default blog
        RegisterRequest registerRequest = new RegisterRequest(
            "test@example.com",
            "Password!123",
            "testuser",
            "Test User",
            LocalDate.of(1990, 1, 1)
        );
        testUser = authService.register(registerRequest);
        testBlog = blogRepository.findDefaultByUserId(testUser.getId()).get();
    }

    @Test
    void shouldGetUserBlog() {
        // When
        BlogSettingsResponse response = blogService.getMe(testUser.getId());

        // Then
        assertThat(response.blogId()).isEqualTo(testBlog.getId());
        assertThat(response.ownerId()).isEqualTo(testUser.getId());
        assertThat(response.urlSlug()).isEqualTo("testuser");
        assertThat(response.isSetupCompleted()).isFalse();
    }

    @Test
    void shouldThrowUserNotFoundWhenGettingNonexistentUserBlog() {
        // When/Then
        assertThatThrownBy(() -> blogService.getMe(99999L))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.USER_001);
    }

    @Test
    void shouldPerformInitialSetup() {
        // When
        BlogInitialSetupRequest request = new BlogInitialSetupRequest(
            "My Blog",
            "my-blog",
            "A blog about my life"
        );
        BlogSettingsResponse response = blogService.initialSetup(testUser.getId(), request);

        // Then
        assertThat(response.title()).isEqualTo("My Blog");
        assertThat(response.urlSlug()).isEqualTo("my-blog");
        assertThat(response.description()).isEqualTo("A blog about my life");
        assertThat(response.isSetupCompleted()).isTrue();
    }

    @Test
    void shouldUseDefaultTitleWhenNotProvidedInSetup() {
        // When
        BlogInitialSetupRequest request = new BlogInitialSetupRequest(
            null,
            "my-blog",
            null
        );
        BlogSettingsResponse response = blogService.initialSetup(testUser.getId(), request);

        // Then
        assertThat(response.title()).isEqualTo("testuser의 블로그");
    }

    @Test
    void shouldUseDefaultUrlSlugWhenNotProvidedInSetup() {
        // When
        BlogInitialSetupRequest request = new BlogInitialSetupRequest(
            "My Blog",
            null,
            "Description"
        );
        BlogSettingsResponse response = blogService.initialSetup(testUser.getId(), request);

        // Then
        assertThat(response.urlSlug()).isEqualTo("testuser");
    }

    @Test
    void shouldRejectInitialSetupIfAlreadySetup() {
        // Given
        BlogInitialSetupRequest firstRequest = new BlogInitialSetupRequest(
            "First Setup",
            "first-setup",
            null
        );
        blogService.initialSetup(testUser.getId(), firstRequest);

        // When/Then
        BlogInitialSetupRequest secondRequest = new BlogInitialSetupRequest(
            "Second Setup",
            "second-setup",
            null
        );
        assertThatThrownBy(() -> blogService.initialSetup(testUser.getId(), secondRequest))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.BLOG_004);
    }

    @Test
    void shouldRejectDuplicateUrlSlugInSetup() {
        // Given: Create another blog
        RegisterRequest otherRequest = new RegisterRequest(
            "other@example.com",
            "Password!456",
            "otheruser",
            "Other User",
            LocalDate.of(1991, 1, 1)
        );
        User otherUser = authService.register(otherRequest);
        Blog otherBlog = blogRepository.findDefaultByUserId(otherUser.getId()).get();
        otherBlog.setupBlog("Other Blog", "duplicate-slug", "Description");
        blogRepository.save(otherBlog);

        // When/Then
        BlogInitialSetupRequest request = new BlogInitialSetupRequest(
            "My Blog",
            "duplicate-slug",
            null
        );
        assertThatThrownBy(() -> blogService.initialSetup(testUser.getId(), request))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.BLOG_002);
    }

    @Test
    void shouldRejectInvalidSlugFormatInSetup() {
        // When/Then
        BlogInitialSetupRequest request = new BlogInitialSetupRequest(
            "My Blog",
            "invalid SLUG!@#",
            null
        );
        assertThatThrownBy(() -> blogService.initialSetup(testUser.getId(), request))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.BLOG_003);
    }

    @Test
    void shouldUpdateBlogWhenSetupCompleted() {
        // Given: Setup blog first
        BlogInitialSetupRequest setupRequest = new BlogInitialSetupRequest(
            "Original Title",
            "original-slug",
            "Original description"
        );
        blogService.initialSetup(testUser.getId(), setupRequest);

        // When
        BlogSettingsRequest updateRequest = new BlogSettingsRequest(
            "Updated Title",
            "updated-slug",
            "Updated description"
        );
        BlogSettingsResponse response = blogService.updateBlog(testUser.getId(), updateRequest);

        // Then
        assertThat(response.title()).isEqualTo("Updated Title");
        assertThat(response.urlSlug()).isEqualTo("updated-slug");
        assertThat(response.description()).isEqualTo("Updated description");
    }

    @Test
    void shouldRejectUpdateBlogIfNotSetupCompleted() {
        // When/Then
        BlogSettingsRequest request = new BlogSettingsRequest(
            "Updated Title",
            "updated-slug",
            "Updated description"
        );
        assertThatThrownBy(() -> blogService.updateBlog(testUser.getId(), request))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.BLOG_004);
    }

    @Test
    void shouldUpdateOnlyTitleWhenUpdatingBlog() {
        // Given: Setup blog first
        BlogInitialSetupRequest setupRequest = new BlogInitialSetupRequest(
            "Original Title",
            "original-slug",
            "Original description"
        );
        blogService.initialSetup(testUser.getId(), setupRequest);

        // When
        BlogSettingsRequest updateRequest = new BlogSettingsRequest(
            "Updated Title",
            null,
            null
        );
        BlogSettingsResponse response = blogService.updateBlog(testUser.getId(), updateRequest);

        // Then
        assertThat(response.title()).isEqualTo("Updated Title");
        assertThat(response.urlSlug()).isEqualTo("original-slug");
        assertThat(response.description()).isEqualTo("Original description");
    }

    @Test
    void shouldRejectDuplicateUrlSlugWhenUpdating() {
        // Given: Create another blog with slug
        RegisterRequest otherRequest = new RegisterRequest(
            "other@example.com",
            "Password!456",
            "otheruser",
            "Other User",
            LocalDate.of(1991, 1, 1)
        );
        User otherUser = authService.register(otherRequest);
        Blog otherBlog = blogRepository.findDefaultByUserId(otherUser.getId()).get();
        otherBlog.setupBlog("Other Blog", "existing-slug", "Description");
        blogRepository.save(otherBlog);

        // Setup first user's blog
        BlogInitialSetupRequest setupRequest = new BlogInitialSetupRequest(
            "My Blog",
            "my-slug",
            "Description"
        );
        blogService.initialSetup(testUser.getId(), setupRequest);

        // When/Then: Try to update to existing slug
        BlogSettingsRequest updateRequest = new BlogSettingsRequest(
            null,
            "existing-slug",
            null
        );
        assertThatThrownBy(() -> blogService.updateBlog(testUser.getId(), updateRequest))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.BLOG_002);
    }

    @Test
    void shouldRejectInvalidSlugFormatWhenUpdating() {
        // Given: Setup blog first
        BlogInitialSetupRequest setupRequest = new BlogInitialSetupRequest(
            "My Blog",
            "my-slug",
            "Description"
        );
        blogService.initialSetup(testUser.getId(), setupRequest);

        // When/Then
        BlogSettingsRequest updateRequest = new BlogSettingsRequest(
            null,
            "invalid SLUG!@#",
            null
        );
        assertThatThrownBy(() -> blogService.updateBlog(testUser.getId(), updateRequest))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.BLOG_003);
    }

    @Test
    void shouldNotRejectSameSlugWhenUpdating() {
        // Given: Setup blog first
        BlogInitialSetupRequest setupRequest = new BlogInitialSetupRequest(
            "My Blog",
            "my-slug",
            "Description"
        );
        blogService.initialSetup(testUser.getId(), setupRequest);

        // When: Update with same slug
        BlogSettingsRequest updateRequest = new BlogSettingsRequest(
            "Updated Title",
            "my-slug",
            "Updated description"
        );
        BlogSettingsResponse response = blogService.updateBlog(testUser.getId(), updateRequest);

        // Then
        assertThat(response.urlSlug()).isEqualTo("my-slug");
        assertThat(response.title()).isEqualTo("Updated Title");
    }

    @Test
    void shouldGetPublicBlogByUrlSlug() {
        // Given: Setup blog
        BlogInitialSetupRequest setupRequest = new BlogInitialSetupRequest(
            "My Blog",
            "my-blog",
            "A nice blog"
        );
        blogService.initialSetup(testUser.getId(), setupRequest);

        // When
        BlogPublicResponse response = blogService.getPublicBlog("my-blog");

        // Then
        assertThat(response.blogId()).isEqualTo(testBlog.getId());
        assertThat(response.title()).isEqualTo("My Blog");
        assertThat(response.urlSlug()).isEqualTo("my-blog");
        assertThat(response.description()).isEqualTo("A nice blog");
        assertThat(response.owner().userId()).isEqualTo(testUser.getId());
        assertThat(response.owner().nickname()).isEqualTo("testuser");
    }

    @Test
    void shouldThrowNotFoundWhenPublicBlogDoesNotExist() {
        // When/Then
        assertThatThrownBy(() -> blogService.getPublicBlog("nonexistent-slug"))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.BLOG_001);
    }

    @Test
    void shouldNotReturnPublicBlogIfUserIsDeleted() {
        // Given: Setup blog
        BlogInitialSetupRequest setupRequest = new BlogInitialSetupRequest(
            "My Blog",
            "my-blog",
            "A nice blog"
        );
        blogService.initialSetup(testUser.getId(), setupRequest);

        // Delete user (soft delete)
        testUser.setDeletedAt(LocalDateTime.now());
        userRepository.save(testUser);

        // When/Then
        assertThatThrownBy(() -> blogService.getPublicBlog("my-blog"))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.BLOG_001);
    }

    @Test
    void shouldReturnPublicBlogIfUserIsSuspended() {
        // Given: Setup blog
        BlogInitialSetupRequest setupRequest = new BlogInitialSetupRequest(
            "My Blog",
            "my-blog",
            "A nice blog"
        );
        blogService.initialSetup(testUser.getId(), setupRequest);

        // Suspend user (NOT a soft delete)
        testUser.suspend();
        userRepository.save(testUser);

        // When
        BlogPublicResponse response = blogService.getPublicBlog("my-blog");

        // Then: Suspended user's blog should still be accessible
        assertThat(response.blogId()).isEqualTo(testBlog.getId());
        assertThat(response.title()).isEqualTo("My Blog");
    }

    @Test
    void shouldIncludeOwnerInfoInPublicBlog() {
        // Given: Setup blog with user details
        testUser.updateProfile(
            null,
            null,
            null,
            "This is my bio",
            "https://example.com/avatar.jpg"
        );
        userRepository.save(testUser);

        BlogInitialSetupRequest setupRequest = new BlogInitialSetupRequest(
            "My Blog",
            "my-blog",
            "Description"
        );
        blogService.initialSetup(testUser.getId(), setupRequest);

        // When
        BlogPublicResponse response = blogService.getPublicBlog("my-blog");

        // Then
        assertThat(response.owner().userId()).isEqualTo(testUser.getId());
        assertThat(response.owner().nickname()).isEqualTo("testuser");
        assertThat(response.owner().bio()).isEqualTo("This is my bio");
        assertThat(response.owner().profileImageUrl()).isEqualTo("https://example.com/avatar.jpg");
    }
}

// Test config to provide BlogService
class TestBlogServiceConfig {
    @org.springframework.context.annotation.Bean
    public BlogService blogService(BlogRepository blogRepository, UserRepository userRepository) {
        return new BlogService(blogRepository, userRepository);
    }
}

// Test config to provide AuthService
class TestAuthServiceConfig {
    @org.springframework.context.annotation.Bean
    public AuthService authService(UserRepository userRepository,
                                   BlogRepository blogRepository,
                                   CategoryRepository categoryRepository) {
        PasswordEncoder encoder = new BCryptPasswordEncoder(12);
        return new AuthService(userRepository, blogRepository, categoryRepository, encoder);
    }
}
