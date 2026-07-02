package com.zeroverse.auth.service;

import com.zeroverse.support.IntegrationTestSupport;

import com.zeroverse.auth.dto.RegisterRequest;
import com.zeroverse.auth.dto.SigninRequest;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(TestAuthServiceConfig.class)
public class AuthServiceTest extends IntegrationTestSupport {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        // Clear repositories
        categoryRepository.deleteAllInBatch();
        blogRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void shouldRegisterNewUser() {
        // When
        RegisterRequest request = new RegisterRequest(
            "newuser@example.com",
            "Password!123",
            "newuser",
            "New User",
            LocalDate.of(1990, 1, 1)
        );
        User registered = authService.register(request);

        // Then
        assertThat(registered.getId()).isNotNull();
        assertThat(registered.getEmail()).isEqualTo("newuser@example.com");
        assertThat(registered.getNickname()).isEqualTo("newuser");
    }

    @Test
    void shouldCreateDefaultBlogDuringRegistration() {
        // When
        RegisterRequest request = new RegisterRequest(
            "newuser@example.com",
            "Password!123",
            "newuser",
            "New User",
            LocalDate.of(1990, 1, 1)
        );
        User registered = authService.register(request);

        // Then
        Blog blog = blogRepository.findDefaultByUserId(registered.getId()).orElse(null);
        assertThat(blog).isNotNull();
        assertThat(blog.getTitle()).isEqualTo("newuser의 블로그");
        assertThat(blog.getUrlSlug()).isEqualTo("newuser");
    }

    @Test
    void shouldCreateDefaultCategoryDuringRegistration() {
        // When
        RegisterRequest request = new RegisterRequest(
            "newuser@example.com",
            "Password!123",
            "newuser",
            "New User",
            LocalDate.of(1990, 1, 1)
        );
        User registered = authService.register(request);
        Blog blog = blogRepository.findDefaultByUserId(registered.getId()).get();

        // Then
        Category defaultCat = categoryRepository.findByBlogIdAndType(blog.getId(), CategoryType.DEFAULT).orElse(null);
        assertThat(defaultCat).isNotNull();
        assertThat(defaultCat.getName()).isEqualTo("미분류");
        assertThat(defaultCat.isDefault()).isTrue();
    }

    @Test
    void shouldRejectDuplicateEmail() {
        // Given
        RegisterRequest request1 = new RegisterRequest(
            "test@example.com",
            "Password!123",
            "user1",
            "User 1",
            LocalDate.of(1990, 1, 1)
        );
        authService.register(request1);

        // When/Then
        RegisterRequest request2 = new RegisterRequest(
            "test@example.com",
            "Password!456",
            "user2",
            "User 2",
            LocalDate.of(1991, 1, 1)
        );
        assertThatThrownBy(() -> authService.register(request2))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.USER_005);
    }

    @Test
    void shouldRejectDuplicateNickname() {
        // Given
        RegisterRequest request1 = new RegisterRequest(
            "user1@example.com",
            "Password!123",
            "duplicate",
            "User 1",
            LocalDate.of(1990, 1, 1)
        );
        authService.register(request1);

        // When/Then
        RegisterRequest request2 = new RegisterRequest(
            "user2@example.com",
            "Password!456",
            "duplicate",
            "User 2",
            LocalDate.of(1991, 1, 1)
        );
        assertThatThrownBy(() -> authService.register(request2))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.USER_006);
    }

    @Test
    void shouldSigninWithCorrectCredentials() {
        // Given
        RegisterRequest registerRequest = new RegisterRequest(
            "test@example.com",
            "Password!123",
            "testuser",
            "Test User",
            LocalDate.of(1990, 1, 1)
        );
        authService.register(registerRequest);

        // When
        SigninRequest signinRequest = new SigninRequest("test@example.com", "Password!123");
        User signedin = authService.signin(signinRequest);

        // Then
        assertThat(signedin.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void shouldRejectSigninWithWrongPassword() {
        // Given
        RegisterRequest registerRequest = new RegisterRequest(
            "test@example.com",
            "Password!123",
            "testuser",
            "Test User",
            LocalDate.of(1990, 1, 1)
        );
        authService.register(registerRequest);

        // When/Then - Wrong password should return AUTH_001 (login failure)
        SigninRequest signinRequest = new SigninRequest("test@example.com", "WrongPassword!123");
        assertThatThrownBy(() -> authService.signin(signinRequest))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.AUTH_001);
    }

    @Test
    void shouldRejectSigninWithNonexistentEmail() {
        // When/Then
        SigninRequest signinRequest = new SigninRequest("nonexistent@example.com", "Password!123");
        assertThatThrownBy(() -> authService.signin(signinRequest))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.AUTH_001);
    }

    @Test
    void shouldRejectSigninWithSuspendedAccountAndWrongPassword() {
        // Given: Suspended user
        RegisterRequest registerRequest = new RegisterRequest(
            "suspended@example.com",
            "Password!123",
            "suspendeduser",
            "Suspended User",
            LocalDate.of(1990, 1, 1)
        );
        User user = authService.register(registerRequest);
        user.suspend();
        userRepository.save(user);

        // When/Then: Wrong password should return generic AUTH_001 (account existence not exposed)
        SigninRequest signinRequest = new SigninRequest("suspended@example.com", "WrongPassword!123");
        assertThatThrownBy(() -> authService.signin(signinRequest))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.AUTH_001);
    }

    @Test
    void shouldRejectSigninWithSuspendedAccountAndCorrectPassword() {
        // Given: Suspended user with correct password
        RegisterRequest registerRequest = new RegisterRequest(
            "suspended@example.com",
            "Password!123",
            "suspendeduser",
            "Suspended User",
            LocalDate.of(1990, 1, 1)
        );
        User user = authService.register(registerRequest);
        user.suspend();
        userRepository.save(user);

        // When/Then: Correct password should return USER_003 (account is authenticated but suspended)
        SigninRequest signinRequest = new SigninRequest("suspended@example.com", "Password!123");
        assertThatThrownBy(() -> authService.signin(signinRequest))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.USER_003);
    }

    @Test
    void shouldEncryptPasswordWithBCrypt() {
        // When
        RegisterRequest request = new RegisterRequest(
            "test@example.com",
            "Password!123",
            "testuser",
            "Test User",
            LocalDate.of(1990, 1, 1)
        );
        User registered = authService.register(request);

        // Then - password should be hashed, not plain text
        User found = userRepository.findByEmail("test@example.com").get();
        assertThat(found.getPassword()).isNotEqualTo("Password!123");
        assertThat(found.getPassword()).startsWith("$2a$"); // BCrypt prefix
    }

    @Test
    void shouldHandleUrlSlugDuplicate() {
        // Given
        RegisterRequest request1 = new RegisterRequest(
            "user1@example.com",
            "Password!123",
            "sameuser",
            "User 1",
            LocalDate.of(1990, 1, 1)
        );
        authService.register(request1);

        // When
        RegisterRequest request2 = new RegisterRequest(
            "user2@example.com",
            "Password!123",
            "sameuser",
            "User 2",
            LocalDate.of(1991, 1, 1)
        );
        assertThatThrownBy(() -> authService.register(request2))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.USER_006);
    }
}

// Test config to provide PasswordEncoder
class TestAuthServiceConfig {
    @org.springframework.context.annotation.Bean
    public AuthService authService(UserRepository userRepository,
                                   BlogRepository blogRepository,
                                   CategoryRepository categoryRepository) {
        PasswordEncoder encoder = new BCryptPasswordEncoder(12);
        return new AuthService(userRepository, blogRepository, categoryRepository, encoder);
    }
}
