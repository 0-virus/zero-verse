package com.zeroverse.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * 블로그 URL slug 생성·검증(REQUIREMENTS §5, PRD §4.5).
 *
 * <p>규칙: 영문 소문자·숫자·하이픈, <b>3~30자</b>, 앞뒤/연속 하이픈 금지, 예약어 금지.
 *
 * <p>nickname 기반 자동 생성 시 비허용 문자는 하이픈으로 바꾸고, 결과가 비었거나 예약어이거나
 * 길이를 못 맞추면 {@code blog}로 대체한다. 중복은 호출자가 {@code -2}, {@code -3} suffix로
 * 해결하며, <b>suffix를 붙인 뒤에도 30자를 넘지 않도록</b> 앞부분을 잘라낸다(ADR-0003 §4).
 */
public final class SlugGenerator {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 30;
    public static final String FALLBACK = "blog";

    private static final Set<String> RESERVED = Set.of(
            "admin", "api", "signin", "signup", "settings", "write", "edit", "search");

    private SlugGenerator() {}

    /** nickname에서 기본 slug 후보를 만든다. 실패하면 {@link #FALLBACK}. */
    public static String fromNickname(String nickname) {
        if (nickname == null) {
            return FALLBACK;
        }
        String normalized = Normalizer.normalize(nickname, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");

        if (normalized.length() > MAX_LENGTH) {
            normalized = normalized.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        if (!isValid(normalized)) {
            return FALLBACK;
        }
        return normalized;
    }

    /**
     * 중복 회피용 후보를 만든다. {@code attempt}가 1이면 base 그대로, 2 이상이면 {@code -N}을 붙인다.
     * suffix 포함 길이가 {@link #MAX_LENGTH}를 넘지 않도록 base를 잘라낸다.
     */
    public static String withSuffix(String base, int attempt) {
        if (attempt <= 1) {
            return base;
        }
        String suffix = "-" + attempt;
        int allowed = MAX_LENGTH - suffix.length();
        String head = base.length() > allowed ? base.substring(0, allowed) : base;
        head = head.replaceAll("-+$", "");
        if (head.isEmpty()) {
            head = FALLBACK;
        }
        return head + suffix;
    }

    public static boolean isValid(String slug) {
        if (slug == null || slug.length() < MIN_LENGTH || slug.length() > MAX_LENGTH) {
            return false;
        }
        if (!slug.matches("[a-z0-9]+(-[a-z0-9]+)*")) {
            return false;
        }
        return !RESERVED.contains(slug);
    }

    public static boolean isReserved(String slug) {
        return RESERVED.contains(slug);
    }
}
