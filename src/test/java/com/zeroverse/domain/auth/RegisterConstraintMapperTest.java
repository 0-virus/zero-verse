package com.zeroverse.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.auth.support.RegisterConstraintMapper;
import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 가입 제약 위반 매핑(심의 필수 변경 #10).
 *
 * <p>이 매핑은 <b>동시 가입에서만</b> 도달하므로 통합 테스트로는 특정 메시지 형태를 결정적으로
 * 재현할 수 없다. 드라이버가 주는 메시지를 직접 넣어 검증한다.
 */
class RegisterConstraintMapperTest {

    @Test
    @DisplayName("이메일 unique 위반은 USER_004다")
    void emailDuplicateMapsToUser004() {
        assertThat(RegisterConstraintMapper.mapMessage(
                        "Duplicate entry 'dup@zeroverse.test' for key 'users.email'"))
                .isEqualTo(ErrorCode.USER_004);
    }

    @Test
    @DisplayName("닉네임 unique 위반은 USER_002다")
    void nicknameDuplicateMapsToUser002() {
        assertThat(RegisterConstraintMapper.mapMessage(
                        "Duplicate entry 'zerostar' for key 'users.nickname'"))
                .isEqualTo(ErrorCode.USER_002);
    }

    @Test
    @DisplayName("slug 충돌은 null을 돌려줘 호출자가 재시도하게 한다")
    void slugDuplicateSignalsRetry() {
        assertThat(RegisterConstraintMapper.mapMessage(
                        "Duplicate entry 'zero-star' for key 'blogs.url_slug'"))
                .isNull();
    }

    /**
     * 회귀 방지의 핵심. 메시지 전체를 {@code contains}로 훑던 이전 구현은 값 부분의
     * {@code 'email'}을 먼저 만나 이메일 중복으로 오분류했다.
     */
    @Nested
    @DisplayName("입력값이 제약 이름과 겹쳐도 오분류하지 않는다")
    class ValueCollidingWithConstraintName {

        @Test
        @DisplayName("닉네임 값이 'email'이어도 USER_002다")
        void nicknameValuedEmail() {
            assertThat(RegisterConstraintMapper.mapMessage(
                            "Duplicate entry 'email' for key 'users.nickname'"))
                    .isEqualTo(ErrorCode.USER_002);
        }

        @Test
        @DisplayName("닉네임 값이 'url_slug'여도 재시도 신호가 아니다")
        void nicknameValuedUrlSlug() {
            assertThat(RegisterConstraintMapper.mapMessage(
                            "Duplicate entry 'url_slug' for key 'users.nickname'"))
                    .isEqualTo(ErrorCode.USER_002);
        }

        @Test
        @DisplayName("이메일 값에 nickname이 들어가도 USER_004다")
        void emailContainingNickname() {
            assertThat(RegisterConstraintMapper.mapMessage(
                            "Duplicate entry 'nickname@zeroverse.test' for key 'users.email'"))
                    .isEqualTo(ErrorCode.USER_004);
        }
    }

    @Test
    @DisplayName("스키마 한정자 없는 옛 MySQL 형식도 받는다")
    void legacyKeyWithoutSchemaQualifier() {
        assertThat(RegisterConstraintMapper.mapMessage("Duplicate entry 'a@b.c' for key 'email'"))
                .isEqualTo(ErrorCode.USER_004);
    }

    @Test
    @DisplayName("명시적 제약 이름(uk_*)도 받는다")
    void explicitConstraintName() {
        assertThat(RegisterConstraintMapper.mapMessage(
                        "Duplicate entry 'x' for key 'uk_users_nickname'"))
                .isEqualTo(ErrorCode.USER_002);
    }

    @Test
    @DisplayName("중복 키가 아닌 무결성 위반은 COMMON_500이다")
    void nonDuplicateViolationIsServerError() {
        assertThat(RegisterConstraintMapper.mapMessage(
                        "Cannot add or update a child row: a foreign key constraint fails"))
                .isEqualTo(ErrorCode.COMMON_500);
    }

    @Test
    @DisplayName("모르는 unique 제약은 도메인 오류로 위장하지 않는다")
    void unknownConstraintIsServerError() {
        assertThat(RegisterConstraintMapper.mapMessage(
                        "Duplicate entry 'x' for key 'posts.some_future_unique'"))
                .isEqualTo(ErrorCode.COMMON_500);
    }

    @Test
    @DisplayName("메시지가 없으면 COMMON_500이다")
    void nullMessageIsServerError() {
        assertThat(RegisterConstraintMapper.mapMessage(null)).isEqualTo(ErrorCode.COMMON_500);
    }

    @Test
    @DisplayName("예외에서 가장 구체적인 원인 메시지를 읽는다")
    void readsMostSpecificCause() {
        var exception = new DataIntegrityViolationException(
                "could not execute statement",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry 'email' for key 'users.nickname'"));

        assertThat(RegisterConstraintMapper.map(exception)).isEqualTo(ErrorCode.USER_002);
    }
}
