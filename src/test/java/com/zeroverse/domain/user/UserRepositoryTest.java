package com.zeroverse.domain.user;

import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.entity.UserStatus;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public class UserRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.create("test@example.com", "hashed_password", "Test User", "testuser", LocalDate.of(1990, 1, 1));
    }

    @Test
    void shouldSaveAndFindUserByEmail() {
        // When
        User saved = userRepository.save(testUser);

        // Then
        Optional<User> found = userRepository.findByEmail("test@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
        assertThat(found.get().getNickname()).isEqualTo("testuser");
    }

    @Test
    void shouldFindUserByNickname() {
        // When
        userRepository.save(testUser);

        // Then
        Optional<User> found = userRepository.findByNickname("testuser");
        assertThat(found).isPresent();
        assertThat(found.get().getNickname()).isEqualTo("testuser");
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        // When
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldCheckEmailExists() {
        // When
        userRepository.save(testUser);

        // Then
        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("other@example.com")).isFalse();
    }

    @Test
    void shouldCheckNicknameExists() {
        // When
        userRepository.save(testUser);

        // Then
        assertThat(userRepository.existsByNickname("testuser")).isTrue();
        assertThat(userRepository.existsByNickname("otheruser")).isFalse();
    }

    @Test
    void shouldExcludeDeletedUserFromSearch() {
        // When
        User saved = userRepository.save(testUser);
        saved.setDeletedAt(java.time.LocalDateTime.now());
        userRepository.save(saved);

        // Then
        assertThat(userRepository.findByEmail("test@example.com")).isEmpty();
        assertThat(userRepository.existsByEmail("test@example.com")).isFalse();
    }

    @Test
    void shouldHaveCorrectDefaultRole() {
        // When
        User saved = userRepository.save(testUser);

        // Then
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void shouldHaveCorrectDefaultStatus() {
        // When
        User saved = userRepository.save(testUser);

        // Then
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
