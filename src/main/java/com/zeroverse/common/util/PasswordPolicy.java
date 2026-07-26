package com.zeroverse.common.util;

/**
 * 비밀번호 정책 정본(REQUIREMENTS FR-AUTH-01, FR-SETTINGS-02).
 *
 * <p>회원가입과 비밀번호 변경은 <b>같은 정책</b>을 쓴다 — FR-SETTINGS-02가 "새 비밀번호는 회원가입과
 * 동일한 정책을 적용한다"고 못 박는다. 그런데 두 DTO가 각자 정규식을 들고 있어 실제로는 서로 달랐다:
 * 회원가입은 모든 비영숫자를 특수문자로 인정했고({@code [^A-Za-z0-9]}), 변경은 열거한 ASCII 기호만
 * 받았다. 그래서 공백이나 한글이 섞인 비밀번호로 가입은 되는데 변경은 400으로 막혔다.
 *
 * <p>정규식을 이 한 곳에 두어 두 엔드포인트가 갈라질 수 없게 한다. 애노테이션 인자로 쓰려면
 * 컴파일 타임 상수여야 하므로 {@code static final String}으로 노출한다.
 */
public final class PasswordPolicy {

    /** 최소 길이. */
    public static final int MIN_LENGTH = 8;

    /** 최대 길이. */
    public static final int MAX_LENGTH = 64;

    /** 영문·숫자·특수문자(= 영숫자가 아닌 모든 문자)를 각각 1자 이상 포함한다. */
    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$";

    /** 길이 위반 메시지. */
    public static final String SIZE_MESSAGE = "비밀번호는 8~64자여야 합니다.";

    /** 구성 위반 메시지. */
    public static final String PATTERN_MESSAGE = "비밀번호는 영문·숫자·특수문자를 모두 포함해야 합니다.";

    private PasswordPolicy() {}
}
