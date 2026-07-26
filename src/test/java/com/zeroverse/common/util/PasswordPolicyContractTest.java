package com.zeroverse.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeroverse.domain.auth.dto.AuthDtos.RegisterRequest;
import com.zeroverse.domain.user.dto.UserSettingsDtos.ChangePasswordRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 회원가입과 비밀번호 변경이 <b>같은</b> 비밀번호 정책을 쓰는지 검증한다(FR-SETTINGS-02).
 *
 * <p>두 DTO가 각자 정규식을 들고 있던 시절 실제로 갈라져 있었다 — 회원가입은 모든 비영숫자를
 * 특수문자로 인정했고 변경은 열거된 ASCII 기호만 받아서, 공백이나 한글이 섞인 비밀번호로 가입은
 * 되는데 변경은 400으로 막혔다. 같은 입력에 두 엔드포인트가 <b>같은 판정</b>을 내리는지 확인해
 * 다시 갈라지면 여기서 잡는다.
 */
class PasswordPolicyContractTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    static Stream<Arguments> passwords() {
        return Stream.of(
                // 기본 통과
                Arguments.of("Passw0rd!", true, "영문·숫자·ASCII 특수문자"),
                // 갈라져 있던 지점 — 비ASCII·공백도 "영숫자가 아닌 문자"로 인정한다
                Arguments.of("Passw0rd한", true, "특수문자 자리에 한글"),
                Arguments.of("Passw0rd ", true, "특수문자 자리에 공백"),
                Arguments.of("Passw0rd★", true, "특수문자 자리에 기호 문자"),
                // 구성 위반
                Arguments.of("Password!", false, "숫자 없음"),
                Arguments.of("12345678!", false, "영문 없음"),
                Arguments.of("Passw0rd", false, "특수문자 없음"),
                // 길이 경계 — inclusive 양끝을 고정한다. 거부 사례만 두면 MIN_LENGTH가 9,
                // MAX_LENGTH가 63으로 잘못 바뀌어도 표본이 통과한다.
                Arguments.of("Pw0rd!ab", true, "정확히 8자"),
                Arguments.of("P1!" + "a".repeat(61), true, "정확히 64자"),
                Arguments.of("Pw0rd!a", false, "7자"),
                Arguments.of("P1!" + "a".repeat(62), false, "65자"));
    }

    @ParameterizedTest(name = "[{index}] {2} → 허용={1}")
    @DisplayName("회원가입과 비밀번호 변경이 같은 입력에 같은 판정을 내린다")
    @MethodSource("passwords")
    void registerAndChangeAgreeOnPolicy(String password, boolean expectedValid, String label) {
        boolean registerValid = violationsOn(register(password), "password").isEmpty();
        boolean changeValid = violationsOn(change(password), "newPassword").isEmpty();

        assertThat(registerValid)
                .as("회원가입 판정 (%s)", label)
                .isEqualTo(expectedValid);
        assertThat(changeValid)
                .as("비밀번호 변경 판정 (%s)", label)
                .isEqualTo(expectedValid);
        assertThat(changeValid)
                .as("두 엔드포인트의 판정이 갈라졌다 (%s)", label)
                .isEqualTo(registerValid);
    }

    private <T> Set<ConstraintViolation<T>> violationsOn(T target, String property) {
        return validator.validateProperty(target, property);
    }

    private RegisterRequest register(String password) {
        return new RegisterRequest(
                "policy@test.com", password, "테스터", "policynick", LocalDate.of(1995, 1, 1));
    }

    private ChangePasswordRequest change(String password) {
        return new ChangePasswordRequest("CurrentPass1!", password);
    }
}
