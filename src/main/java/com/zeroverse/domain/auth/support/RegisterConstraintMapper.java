package com.zeroverse.domain.auth.support;

import com.zeroverse.common.exception.ErrorCode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 가입 시 DB 제약 위반을 도메인 오류로 매핑한다(FR-AUTH-01, 심의 필수 변경 #10).
 *
 * <p>{@code V1__init.sql}이 {@code users.email}, {@code users.nickname}, {@code blogs.url_slug}에
 * unique를 걸어 두었다. 선조회는 동시 가입을 막지 못하므로 DB 제약이 최종 방어선이고, 그 예외를
 * 그대로 두면 {@code COMMON_500}이 된다.
 *
 * <p><b>서비스에서 분리한 이유</b>: 이 매핑은 <b>동시 가입에서만</b> 도달한다 — 순차 요청은
 * 선조회에서 먼저 걸러진다. 서비스의 private 메서드로 두면 통합 테스트로는 특정 메시지 형태를
 * 결정적으로 재현할 수 없어, 실제로는 매핑을 검증하지 못하는 테스트가 만들어진다. 별도 타입으로
 * 빼서 오류 메시지를 직접 넣어 단위 검증한다.
 */
public final class RegisterConstraintMapper {

    private static final Logger log = LoggerFactory.getLogger(RegisterConstraintMapper.class);

    /**
     * MySQL 중복 키 오류에서 <b>제약 이름</b>만 뽑는다.
     *
     * <p>메시지 형식: {@code Duplicate entry 'zerostar' for key 'users.nickname'}.
     *
     * <p>메시지 전체를 {@code contains}로 훑으면 <b>입력값이 제약 이름과 겹칠 때 오분류</b>된다 —
     * 닉네임이 {@code email}인 사용자가 중복되면 {@code Duplicate entry 'email' for key
     * 'users.nickname'}이 되어 이메일 중복으로 잘못 판정된다. 값 부분을 배제하고 키 이름만 본다.
     */
    private static final Pattern DUPLICATE_KEY =
            Pattern.compile("for key '([^']+)'", Pattern.CASE_INSENSITIVE);

    private RegisterConstraintMapper() {}

    /**
     * @return 매핑된 오류. {@code null}이면 slug 충돌이므로 호출자가 다른 후보로 재시도한다
     */
    public static ErrorCode map(DataIntegrityViolationException e) {
        return mapMessage(e.getMostSpecificCause().getMessage());
    }

    /**
     * 오류 메시지만으로 판정한다. 예외 래핑 없이 검증할 수 있도록 분리했다.
     *
     * @param message 드라이버가 준 가장 구체적인 원인 메시지. {@code null}일 수 있다
     */
    public static ErrorCode mapMessage(String message) {
        if (message == null) {
            return ErrorCode.COMMON_500;
        }

        Matcher matcher = DUPLICATE_KEY.matcher(message);
        if (!matcher.find()) {
            // 중복 키가 아닌 무결성 위반(FK·NOT NULL 등)은 서버 오류다.
            log.warn("가입 실패: 매핑되지 않은 무결성 위반");
            return ErrorCode.COMMON_500;
        }

        // MySQL 8은 `users.nickname`, 이전 버전은 `nickname` 형태로 준다. 둘 다 받는다.
        String key = matcher.group(1).toLowerCase(Locale.ROOT);
        String column = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;

        return switch (column) {
            case "url_slug", "uk_blogs_url_slug" -> null;
            case "email", "uk_users_email" -> ErrorCode.USER_004;
            case "nickname", "uk_users_nickname" -> ErrorCode.USER_002;
            default -> {
                log.warn("가입 실패: 알 수 없는 unique 제약 key={}", key);
                yield ErrorCode.COMMON_500;
            }
        };
    }
}
