package com.zeroverse.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** slug 생성·검증(REQUIREMENTS §5, ADR-0003 §4). */
class SlugGeneratorTest {

    @ParameterizedTest
    @CsvSource({
        "zerostar, zerostar",
        "Zero Star, zero-star",
        "ZERO__STAR, zero-star",
        "  zero-star  , zero-star",
        "zero.star, zero-star"
    })
    @DisplayName("nickname을 소문자·하이픈 slug로 정규화한다")
    void normalizesNickname(String nickname, String expected) {
        assertThat(SlugGenerator.fromNickname(nickname)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"제로별", "!!!", "-", "ab"})
    @DisplayName("정규화 결과가 비거나 최소 길이에 못 미치면 fallback을 쓴다")
    void fallsBackWhenNormalizationFails(String nickname) {
        assertThat(SlugGenerator.fromNickname(nickname)).isEqualTo(SlugGenerator.FALLBACK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "api", "signin", "signup", "settings", "write", "edit", "search"})
    @DisplayName("예약어는 fallback으로 대체한다")
    void reservedWordsFallBack(String reserved) {
        assertThat(SlugGenerator.isReserved(reserved)).isTrue();
        assertThat(SlugGenerator.fromNickname(reserved)).isEqualTo(SlugGenerator.FALLBACK);
    }

    @Test
    @DisplayName("30자를 넘으면 잘라내고 끝의 하이픈을 제거한다")
    void truncatesToMaxLength() {
        String slug = SlugGenerator.fromNickname("a".repeat(40));

        assertThat(slug).hasSize(SlugGenerator.MAX_LENGTH);
        assertThat(slug).doesNotEndWith("-");
    }

    @Test
    @DisplayName("suffix를 붙여도 30자를 넘지 않는다")
    void suffixNeverExceedsMaxLength() {
        String base = "a".repeat(SlugGenerator.MAX_LENGTH);

        for (int attempt = 2; attempt <= 100; attempt++) {
            String candidate = SlugGenerator.withSuffix(base, attempt);

            assertThat(candidate.length())
                    .as("attempt=%d 의 길이", attempt)
                    .isLessThanOrEqualTo(SlugGenerator.MAX_LENGTH);
            assertThat(candidate).endsWith("-" + attempt);
            assertThat(SlugGenerator.isValid(candidate)).isTrue();
        }
    }

    @Test
    @DisplayName("attempt 1은 base를 그대로 쓴다")
    void firstAttemptUsesBase() {
        assertThat(SlugGenerator.withSuffix("zerostar", 1)).isEqualTo("zerostar");
    }

    @Test
    @DisplayName("자르고 남은 끝이 하이픈이면 제거해 연속·후행 하이픈을 만들지 않는다")
    void suffixDoesNotProduceTrailingHyphenBeforeSuffix() {
        String base = "abcdefghij-klmnopqrst-uvwxyz-ab";

        String candidate = SlugGenerator.withSuffix(base, 5);

        assertThat(candidate).doesNotContain("--");
        assertThat(SlugGenerator.isValid(candidate)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "ab, false",
        "abc, true",
        "-abc, false",
        "abc-, false",
        "ab--c, false",
        "ABC, false",
        "abc_def, false",
        "admin, false"
    })
    @DisplayName("slug 형식 규칙을 검증한다")
    void validatesSlugFormat(String slug, boolean expected) {
        assertThat(SlugGenerator.isValid(slug)).isEqualTo(expected);
    }
}
