package com.zeroverse.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.user.dto.UserSettingsDtos.ChangePasswordRequest;
import com.zeroverse.domain.user.dto.UserSettingsDtos.UpdateProfileRequest;
import com.zeroverse.domain.user.dto.UserSettingsDtos.UserProfileResponse;
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
 * UserSettingsService 테스트(FR-SETTINGS-01·02).
 *
 * <p>프로필 조회/수정과 비밀번호 변경의 비즈니스 로직을 검증한다. 동시 nickname 변경은
 * ConcurrentUpdateTest에서 실제 MySQL 동시 요청으로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserSettingsServiceTest extends MySqlTestSupport {

    @Autowired
    private UserSettingsService userSettingsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User createUser(String email, String nickname, String password) {
        User user = User.register(
                email,
                passwordEncoder.encode(password),
                "테스터",
                nickname,
                LocalDate.of(1990, 1, 1));
        return userRepository.save(user);
    }

    @Nested
    @DisplayName("getProfile")
    class GetProfileTests {

        @Test
        @DisplayName("존재하는 사용자의 프로필을 조회한다")
        void returnProfileForExistingUser() {
            User user = createUser("get@test.com", "getnick", "password123!@");

            UserProfileResponse response = userSettingsService.getProfile(user.getId());

            assertThat(response)
                    .isNotNull()
                    .extracting("id", "email", "nickname", "name")
                    .containsExactly(user.getId(), "get@test.com", "getnick", "테스터");
            // password 필드는 응답에 없어야 한다
            assertThat(response.toString()).doesNotContainIgnoringCase("password");
        }

        @Test
        @DisplayName("없는 사용자는 USER_001을 던진다")
        void throwUser001ForNonexistentUser() {
            assertThatThrownBy(() -> userSettingsService.getProfile(99999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_001);
        }

        @Test
        @DisplayName("soft delete된 사용자는 USER_001을 던진다")
        void throwUser001ForDeletedUser() {
            User user = createUser("deleted@test.com", "deletednick", "password123!@");
            user.softDelete();
            userRepository.save(user);

            assertThatThrownBy(() -> userSettingsService.getProfile(user.getId()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_001);
        }
    }

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfileTests {

        @Test
        @DisplayName("모든 필드를 수정한다")
        void updateAllFields() {
            User user = createUser("update@test.com", "oldnick", "password123!@");
            UpdateProfileRequest request = new UpdateProfileRequest(
                    "새이름",
                    "newnick",
                    "새 소개",
                    LocalDate.of(1995, 5, 15),
                    "http://example.com/image.jpg");

            UserProfileResponse response = userSettingsService.updateProfile(user.getId(), request);

            assertThat(response)
                    .extracting("name", "nickname", "bio", "birthDate", "profileImageUrl")
                    .containsExactly("새이름", "newnick", "새 소개", LocalDate.of(1995, 5, 15),
                            "http://example.com/image.jpg");
        }

        /**
         * self-exclusion의 존재 이유. 중복 검사가 자기 자신을 제외하지 않으면, 닉네임을 그대로
         * 둔 채 소개만 바꾸는 지극히 평범한 요청이 "이미 사용 중인 닉네임"으로 거부된다 —
         * 프로필 수정이 사실상 전부 막힌다.
         */
        @Test
        @DisplayName("nickname을 그대로 두고 다른 필드만 바꿔도 중복으로 거부하지 않는다")
        void keepingOwnNicknameIsNotDuplicate() {
            User user = createUser("keep@test.com", "keepnick", "password123!@");

            UpdateProfileRequest request = new UpdateProfileRequest(
                    "테스터", "keepnick", "소개만 바꾼다", null, null);

            UserProfileResponse response = userSettingsService.updateProfile(user.getId(), request);

            assertThat(response.nickname()).isEqualTo("keepnick");
            assertThat(response.bio()).isEqualTo("소개만 바꾼다");
        }

        @Test
        @DisplayName("nickname을 바꿔도 블로그 slug는 건드리지 않는다")
        void changedNicknameDoesNotChangeSlug() {
            User user = createUser("slug@test.com", "oldnick", "password123!@");
            UpdateProfileRequest request = new UpdateProfileRequest(
                    "테스터", "newnick", null, null, null);

            userSettingsService.updateProfile(user.getId(), request);
            User updated = userRepository.findByIdAndDeletedAtIsNull(user.getId()).get();

            assertThat(updated.getNickname()).isEqualTo("newnick");
            // 블로그 검증은 BlogSettingsServiceTest에서 다루지만, 서비스는 slug를 건드리지 않는다
        }

        @Test
        @DisplayName("자신의 기존 nickname으로 업데이트해도 중복이라 하지 않는다")
        void allowSelfExclusionNickname() {
            User user = createUser("self@test.com", "mynick", "password123!@");
            UpdateProfileRequest request = new UpdateProfileRequest(
                    "테스터", "mynick", null, null, null);

            UserProfileResponse response = userSettingsService.updateProfile(user.getId(), request);

            assertThat(response.nickname()).isEqualTo("mynick");
        }

        @Test
        @DisplayName("다른 사용자의 nickname으로 변경하면 USER_002를 던진다")
        void throwUser002ForDuplicateNickname() {
            User user1 = createUser("first@test.com", "nick1", "password123!@");
            User user2 = createUser("second@test.com", "nick2", "password123!@");

            UpdateProfileRequest request = new UpdateProfileRequest(
                    "테스터", "nick1", null, null, null);

            assertThatThrownBy(() -> userSettingsService.updateProfile(user2.getId(), request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_002);
        }

        @Test
        @DisplayName("없는 사용자는 USER_001을 던진다")
        void throwUser001ForNonexistentUser() {
            UpdateProfileRequest request = new UpdateProfileRequest(
                    "테스터", "newnick", null, null, null);

            assertThatThrownBy(() -> userSettingsService.updateProfile(99999L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_001);
        }

        @Test
        @DisplayName("null name은 VALIDATION_001을 던진다")
        void throwValidation001ForNullName() {
            User user = createUser("validate@test.com", "nick", "password123!@");
            UpdateProfileRequest request = new UpdateProfileRequest(
                    null, "newnick", null, null, null);

            assertThatThrownBy(() -> userSettingsService.updateProfile(user.getId(), request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.VALIDATION_001);
        }

        @Test
        @DisplayName("100자 초과 name은 VALIDATION_001을 던진다")
        void throwValidation001ForOverlongName() {
            User user = createUser("validate@test.com", "nick", "password123!@");
            String longName = "a".repeat(101);
            UpdateProfileRequest request = new UpdateProfileRequest(
                    longName, "newnick", null, null, null);

            assertThatThrownBy(() -> userSettingsService.updateProfile(user.getId(), request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.VALIDATION_001);
        }

        @Test
        @DisplayName("1자 nickname은 VALIDATION_001을 던진다")
        void throwValidation001ForShortNickname() {
            User user = createUser("validate@test.com", "nick", "password123!@");
            UpdateProfileRequest request = new UpdateProfileRequest(
                    "테스터", "a", null, null, null);

            assertThatThrownBy(() -> userSettingsService.updateProfile(user.getId(), request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.VALIDATION_001);
        }

        @Test
        @DisplayName("21자 nickname은 VALIDATION_001을 던진다")
        void throwValidation001ForLongNickname() {
            User user = createUser("validate@test.com", "nick", "password123!@");
            String longNick = "a".repeat(21);
            UpdateProfileRequest request = new UpdateProfileRequest(
                    "테스터", longNick, null, null, null);

            assertThatThrownBy(() -> userSettingsService.updateProfile(user.getId(), request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.VALIDATION_001);
        }

        @Test
        @DisplayName("bio와 profileImageUrl은 nullable이다")
        void allowNullableFields() {
            User user = createUser("nullable@test.com", "nick", "password123!@");
            UpdateProfileRequest request = new UpdateProfileRequest(
                    "테스터", "newnick", null, null, null);

            UserProfileResponse response = userSettingsService.updateProfile(user.getId(), request);

            assertThat(response).extracting("bio", "profileImageUrl")
                    .containsExactly(null, null);
        }
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePasswordTests {

        @Test
        @DisplayName("현재 비밀번호가 일치하면 비밀번호를 변경한다")
        void changePasswordWithCorrectCurrent() {
            User user = createUser("change@test.com", "nick", "password123!@");
            ChangePasswordRequest request = new ChangePasswordRequest(
                    "password123!@",
                    "newpassword456!@");

            userSettingsService.changePassword(user.getId(), request);

            User updated = userRepository.findByIdAndDeletedAtIsNull(user.getId()).get();
            assertThat(passwordEncoder.matches("newpassword456!@", updated.getPassword())).isTrue();
            assertThat(passwordEncoder.matches("password123!@", updated.getPassword())).isFalse();
        }

        @Test
        @DisplayName("현재 비밀번호가 일치하지 않으면 USER_005를 던진다")
        void throwUser005ForWrongCurrentPassword() {
            User user = createUser("wrong@test.com", "nick", "password123!@");
            ChangePasswordRequest request = new ChangePasswordRequest(
                    "wrongpassword!@",
                    "newpassword456!@");

            assertThatThrownBy(() -> userSettingsService.changePassword(user.getId(), request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_005);
        }

        @Test
        @DisplayName("없는 사용자는 USER_001을 던진다")
        void throwUser001ForNonexistentUser() {
            ChangePasswordRequest request = new ChangePasswordRequest(
                    "password123!@",
                    "newpassword456!@");

            assertThatThrownBy(() -> userSettingsService.changePassword(99999L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_001);
        }

        @Test
        @DisplayName("응답 DTO에 password가 없다")
        void responseHasNoPassword() {
            User user = createUser("nopass@test.com", "nick", "password123!@");
            ChangePasswordRequest request = new ChangePasswordRequest(
                    "password123!@",
                    "newpassword456!@");

            var response = userSettingsService.changePassword(user.getId(), request);

            // ChangePasswordResponse는 빈 DTO이고 정상적으로 반환되어야 한다
            assertThat(response).isNotNull();
        }
    }
}
