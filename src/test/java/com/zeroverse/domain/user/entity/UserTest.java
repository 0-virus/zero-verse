package com.zeroverse.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * User 엔티티 단위 테스트(FR-SETTINGS-01, FR-SETTINGS-02).
 *
 * <p>프로필 변경과 비밀번호 변경의 불변식을 검증하며, 필드 유효성은 엔티티 레벨에서 방어한다.
 * 모든 검증 실패는 BusinessException으로 400(VALIDATION_001)을 반환한다.
 */
@DisplayName("User 엔티티")
class UserTest {

    private static final LocalDate TEST_BIRTH_DATE = LocalDate.of(1990, 1, 1);

    private User createTestUser() {
        return User.register("test@example.com", "$2a$12$hashedPassword", "테스트", "testuser",
                            TEST_BIRTH_DATE);
    }

    @Nested
    @DisplayName("updateProfile 메서드")
    class UpdateProfileTests {

        @Test
        @DisplayName("모든 필드를 변경할 수 있다")
        void updateAllFields() {
            User user = createTestUser();

            user.updateProfile("새로운이름", "newNick", "새로운소개", LocalDate.of(1995, 5, 5),
                              "https://example.com/avatar.jpg");

            assertThat(user.getName()).isEqualTo("새로운이름");
            assertThat(user.getNickname()).isEqualTo("newNick");
            assertThat(user.getBio()).isEqualTo("새로운소개");
            assertThat(user.getBirthDate()).isEqualTo(LocalDate.of(1995, 5, 5));
            assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/avatar.jpg");
        }

        @Test
        @DisplayName("이름과 닉네임만 변경하고 나머지는 null로 유지할 수 있다")
        void updateNameAndNicknameWithNullOthers() {
            User user = createTestUser();
            user.updateProfile("새이름", "newNi", null, null, null);

            assertThat(user.getName()).isEqualTo("새이름");
            assertThat(user.getNickname()).isEqualTo("newNi");
            assertThat(user.getBio()).isNull();
            assertThat(user.getBirthDate()).isNull();
            assertThat(user.getProfileImageUrl()).isNull();
        }

        @Test
        @DisplayName("닉네임 변경은 블로그 slug에 영향을 주지 않는다")
        void nicknameChangeDoesNotAffectBlogSlug() {
            User user = createTestUser();
            assertThat(user.getNickname()).isEqualTo("testuser");

            user.updateProfile("테스트", "brandnew", null, TEST_BIRTH_DATE, null);

            assertThat(user.getNickname()).isEqualTo("brandnew");
        }

        // --- name 검증 ---

        @Test
        @DisplayName("name이 null이면 BusinessException(VALIDATION_001)을 던진다")
        void nameNullThrows() {
            User user = createTestUser();

            assertThatThrownBy(() -> user.updateProfile(null, "newNick", null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_001));
        }

        @Test
        @DisplayName("name이 공백이면 BusinessException(VALIDATION_001)을 던진다")
        void nameBlankThrows() {
            User user = createTestUser();

            assertThatThrownBy(() -> user.updateProfile("   ", "newNick", null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_001));
        }

        @ParameterizedTest
        @ValueSource(ints = {99, 100})
        @DisplayName("name이 1~100자일 때 성공한다")
        void nameWithinBoundary(int length) {
            User user = createTestUser();
            String name = "a".repeat(length);

            user.updateProfile(name, "nick", null, null, null);

            assertThat(user.getName()).isEqualTo(name);
        }

        @Test
        @DisplayName("name이 101자이면 BusinessException(VALIDATION_001)을 던진다")
        void nameExceedsMaxLength() {
            User user = createTestUser();
            String tooLongName = "a".repeat(101);

            assertThatThrownBy(() -> user.updateProfile(tooLongName, "nick", null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_001));
        }

        // --- nickname 검증 (2~20자) ---

        @Test
        @DisplayName("nickname이 null이면 BusinessException(VALIDATION_001)을 던진다")
        void nicknameNullThrows() {
            User user = createTestUser();

            assertThatThrownBy(() -> user.updateProfile("name", null, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_001));
        }

        @Test
        @DisplayName("nickname이 공백이면 BusinessException(VALIDATION_001)을 던진다")
        void nicknameBlankThrows() {
            User user = createTestUser();

            assertThatThrownBy(() -> user.updateProfile("name", "  ", null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_001));
        }

        @Test
        @DisplayName("nickname이 1자이면 BusinessException(VALIDATION_001)을 던진다 (최소 2자)")
        void nicknameBelow2CharsThrows() {
            User user = createTestUser();

            assertThatThrownBy(() -> user.updateProfile("name", "a", null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_001));
        }

        @ParameterizedTest
        @ValueSource(ints = {2, 20})
        @DisplayName("nickname이 2~20자일 때 성공한다")
        void nicknameWithinBoundary(int length) {
            User user = createTestUser();
            String nickname = "n".repeat(length);

            user.updateProfile("name", nickname, null, null, null);

            assertThat(user.getNickname()).isEqualTo(nickname);
        }

        @Test
        @DisplayName("nickname이 21자이면 BusinessException(VALIDATION_001)을 던진다 (최대 20자)")
        void nicknameExceeds20CharsThrows() {
            User user = createTestUser();
            String tooLongNickname = "n".repeat(21);

            assertThatThrownBy(() -> user.updateProfile("name", tooLongNickname, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_001));
        }
    }

    @Nested
    @DisplayName("changePassword 메서드")
    class ChangePasswordTests {

        @Test
        @DisplayName("비밀번호를 새로운 해시로 변경할 수 있다")
        void changePasswordToNewHash() {
            User user = createTestUser();
            String oldPassword = user.getPassword();
            String newPassword = "$2a$12$newHashedPassword";

            user.changePassword(newPassword);

            assertThat(user.getPassword()).isEqualTo(newPassword).isNotEqualTo(oldPassword);
        }

        @Test
        @DisplayName("encodedPassword가 null이면 BusinessException(VALIDATION_001)을 던진다")
        void encodedPasswordNullThrows() {
            User user = createTestUser();

            assertThatThrownBy(() -> user.changePassword(null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_001));
        }

        @Test
        @DisplayName("encodedPassword가 공백이면 BusinessException(VALIDATION_001)을 던진다")
        void encodedPasswordBlankThrows() {
            User user = createTestUser();

            assertThatThrownBy(() -> user.changePassword("   "))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_001));
        }

        @Test
        @DisplayName("비어있는 문자열을 전달하면 BusinessException(VALIDATION_001)을 던진다")
        void encodedPasswordEmptyStringThrows() {
            User user = createTestUser();

            assertThatThrownBy(() -> user.changePassword(""))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_001));
        }
    }

    @Nested
    @DisplayName("Mutation 테스트 — updateProfile nickname 상한 검증")
    class UpdateProfileMutationTests {

        @Test
        @DisplayName("nickname 상한 20자 검증 생략 시 실패한다")
        void mutationDetectNicknameMaxLength20Validation() {
            User user = createTestUser();
            String exactly20 = "n".repeat(20);

            // 20자는 성공해야 함
            user.updateProfile("name", exactly20, null, null, null);
            assertThat(user.getNickname()).isEqualTo(exactly20);

            // 재생성해야 함
            User user2 = createTestUser();
            String tooLong21 = "n".repeat(21);

            // 21자는 거부되어야 함
            assertThatThrownBy(() -> user2.updateProfile("name", tooLong21, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_001));
        }

        @Test
        @DisplayName("nickname 하한 2자 검증 생략 시 실패한다")
        void mutationDetectNicknameMinLength2Validation() {
            User user = createTestUser();

            // 1자는 거부되어야 함
            assertThatThrownBy(() -> user.updateProfile("name", "a", null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_001));
        }
    }

    @Nested
    @DisplayName("Mutation 테스트 — changePassword 할당")
    class ChangePasswordMutationTests {

        @Test
        @DisplayName("password 할당 생략 시 실패한다")
        void mutationDetectPasswordAssignmentSkip() {
            User user = createTestUser();
            String original = user.getPassword();
            String newHash = "$2a$12$newHash123";

            user.changePassword(newHash);

            assertThat(user.getPassword()).isNotEqualTo(original).isEqualTo(newHash);
        }
    }
}
