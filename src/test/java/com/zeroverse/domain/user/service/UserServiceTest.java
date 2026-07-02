package com.zeroverse.domain.user.service;

import com.zeroverse.auth.dto.RegisterRequest;
import com.zeroverse.auth.service.AuthService;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.dto.user.ChangePasswordRequest;
import com.zeroverse.dto.user.UserSettingsRequest;
import com.zeroverse.dto.user.UserSettingsResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestUserServiceConfig.class, TestAuthServiceConfig.class})
public class UserServiceTest extends IntegrationTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Clear repositories
        categoryRepository.deleteAllInBatch();
        blogRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // Create test user
        RegisterRequest registerRequest = new RegisterRequest(
            "test@example.com",
            "Password!123",
            "testuser",
            "Test User",
            LocalDate.of(1990, 1, 1)
        );
        testUser = authService.register(registerRequest);
    }

    @Test
    void shouldGetCurrentUserInfo() {
        // When
        UserSettingsResponse response = userService.getMe(testUser.getId());

        // Then
        assertThat(response.userId()).isEqualTo(testUser.getId());
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.nickname()).isEqualTo("testuser");
        assertThat(response.name()).isEqualTo("Test User");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
    }

    @Test
    void shouldThrowUserNotFoundWhenGettingNonexistentUser() {
        // When/Then
        assertThatThrownBy(() -> userService.getMe(99999L))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.USER_001);
    }

    @Test
    void shouldUpdateUserName() {
        // When
        UserSettingsRequest request = new UserSettingsRequest(
            "Updated Name",
            null,
            null,
            null,
            null
        );
        UserSettingsResponse response = userService.updateProfile(testUser.getId(), request);

        // Then
        assertThat(response.name()).isEqualTo("Updated Name");
        User updated = userRepository.findById(testUser.getId()).get();
        assertThat(updated.getName()).isEqualTo("Updated Name");
    }

    @Test
    void shouldUpdateUserNickname() {
        // When
        UserSettingsRequest request = new UserSettingsRequest(
            null,
            "newnickname",
            null,
            null,
            null
        );
        UserSettingsResponse response = userService.updateProfile(testUser.getId(), request);

        // Then
        assertThat(response.nickname()).isEqualTo("newnickname");
        User updated = userRepository.findById(testUser.getId()).get();
        assertThat(updated.getNickname()).isEqualTo("newnickname");
    }

    @Test
    void shouldRejectDuplicateNicknameUpdate() {
        // Given: another user with different nickname
        RegisterRequest registerRequest = new RegisterRequest(
            "other@example.com",
            "Password!456",
            "otheruser",
            "Other User",
            LocalDate.of(1991, 1, 1)
        );
        authService.register(registerRequest);

        // When/Then: attempt to update to existing nickname should fail
        UserSettingsRequest request = new UserSettingsRequest(
            null,
            "otheruser",
            null,
            null,
            null
        );
        assertThatThrownBy(() -> userService.updateProfile(testUser.getId(), request))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.USER_002);
    }

    @Test
    void shouldUpdateUserBio() {
        // When
        UserSettingsRequest request = new UserSettingsRequest(
            null,
            null,
            null,
            "This is my bio",
            null
        );
        UserSettingsResponse response = userService.updateProfile(testUser.getId(), request);

        // Then
        assertThat(response.bio()).isEqualTo("This is my bio");
    }

    @Test
    void shouldUpdateUserProfileImageUrl() {
        // When
        UserSettingsRequest request = new UserSettingsRequest(
            null,
            null,
            null,
            null,
            "https://example.com/image.jpg"
        );
        UserSettingsResponse response = userService.updateProfile(testUser.getId(), request);

        // Then
        assertThat(response.profileImageUrl()).isEqualTo("https://example.com/image.jpg");
    }

    @Test
    void shouldUpdateUserBirthDate() {
        // When
        UserSettingsRequest request = new UserSettingsRequest(
            null,
            null,
            LocalDate.of(1995, 5, 15),
            null,
            null
        );
        UserSettingsResponse response = userService.updateProfile(testUser.getId(), request);

        // Then
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1995, 5, 15));
    }

    @Test
    void shouldChangePassword() {
        // Given
        String originalPassword = testUser.getPassword();

        // When
        ChangePasswordRequest request = new ChangePasswordRequest(
            "Password!123",
            "NewPassword!456"
        );
        userService.changePassword(testUser.getId(), request);

        // Then
        User updated = userRepository.findById(testUser.getId()).get();

        // Verify new password works and old password doesn't
        PasswordEncoder encoder = new BCryptPasswordEncoder(12);
        assertThat(encoder.matches("NewPassword!456", updated.getPassword())).isTrue();
        assertThat(encoder.matches("Password!123", updated.getPassword())).isFalse();
    }

    @Test
    void shouldRejectWrongCurrentPasswordWhenChangingPassword() {
        // When/Then
        ChangePasswordRequest request = new ChangePasswordRequest(
            "WrongPassword!999",
            "NewPassword!456"
        );
        assertThatThrownBy(() -> userService.changePassword(testUser.getId(), request))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.AUTH_001);
    }

    @Test
    void shouldThrowUserNotFoundWhenChangingPasswordOfNonexistentUser() {
        // When/Then
        ChangePasswordRequest request = new ChangePasswordRequest(
            "Password!123",
            "NewPassword!456"
        );
        assertThatThrownBy(() -> userService.changePassword(99999L, request))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.USER_001);
    }

    @Test
    void shouldNotChangeSameNicknameOnUpdate() {
        // When: Update with same nickname (should not cause error)
        UserSettingsRequest request = new UserSettingsRequest(
            "Updated Name",
            "testuser",
            null,
            null,
            null
        );
        UserSettingsResponse response = userService.updateProfile(testUser.getId(), request);

        // Then
        assertThat(response.nickname()).isEqualTo("testuser");
        assertThat(response.name()).isEqualTo("Updated Name");
    }

    @Test
    void shouldUpdateMultipleFieldsTogether() {
        // When
        UserSettingsRequest request = new UserSettingsRequest(
            "New Name",
            "newnickname",
            LocalDate.of(1992, 6, 15),
            "Updated bio",
            "https://example.com/new-image.jpg"
        );
        UserSettingsResponse response = userService.updateProfile(testUser.getId(), request);

        // Then
        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.nickname()).isEqualTo("newnickname");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1992, 6, 15));
        assertThat(response.bio()).isEqualTo("Updated bio");
        assertThat(response.profileImageUrl()).isEqualTo("https://example.com/new-image.jpg");
    }
}

// Test config to provide UserService
class TestUserServiceConfig {
    @org.springframework.context.annotation.Bean
    public UserService userService(UserRepository userRepository) {
        PasswordEncoder encoder = new BCryptPasswordEncoder(12);
        return new UserService(userRepository, encoder);
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
