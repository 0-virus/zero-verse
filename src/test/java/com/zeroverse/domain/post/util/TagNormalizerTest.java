package com.zeroverse.domain.post.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TagNormalizer 테스트")
class TagNormalizerTest {

    @Test
    @DisplayName("태그를 trim + lowercase로 정규화한다")
    void testNormalize() {
        List<String> input = Arrays.asList(" Java ", "SPRING", " jwt ");
        List<String> normalized = TagNormalizer.normalize(input);

        assertThat(normalized).hasSize(3)
            .contains("java", "spring", "jwt");
    }

    @Test
    @DisplayName("중복된 태그를 제거한다")
    void testRemoveDuplicates() {
        List<String> input = Arrays.asList(" Java ", "java", "JAVA");
        List<String> normalized = TagNormalizer.normalize(input);

        assertThat(normalized).hasSize(1)
            .contains("java");
    }

    @Test
    @DisplayName("빈 태그를 제외한다")
    void testIgnoreEmpty() {
        List<String> input = Arrays.asList("java", "", null, "  ", "spring");
        List<String> normalized = TagNormalizer.normalize(input);

        assertThat(normalized).hasSize(2)
            .contains("java", "spring");
    }

    @Test
    @DisplayName("null 입력은 빈 리스트를 반환한다")
    void testNullInput() {
        List<String> normalized = TagNormalizer.normalize(null);

        assertThat(normalized).isEmpty();
    }

    @Test
    @DisplayName("단일 태그를 정규화한다")
    void testNormalizeSingle() {
        String normalized = TagNormalizer.normalizeSingle(" Java ");

        assertThat(normalized).isEqualTo("java");
    }
}
