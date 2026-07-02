package com.zeroverse.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugGeneratorTest {

    @Test
    void shouldNormalizeBasicLowercaseAscii() {
        String result = SlugGenerator.normalize("testuser");
        assertThat(result).isEqualTo("testuser");
    }

    @Test
    void shouldConvertUppercaseToLowercase() {
        String result = SlugGenerator.normalize("TestUser");
        assertThat(result).isEqualTo("testuser");
    }

    @Test
    void shouldReplaceSpecialCharactersWithHyphens() {
        String result = SlugGenerator.normalize("test@user!");
        assertThat(result).isEqualTo("test-user");
    }

    @Test
    void shouldReplaceSpacesWithHyphens() {
        String result = SlugGenerator.normalize("test user");
        assertThat(result).isEqualTo("test-user");
    }

    @Test
    void shouldRemoveLeadingTrailingHyphens() {
        String result = SlugGenerator.normalize("-testuser-");
        assertThat(result).isEqualTo("testuser");
    }

    @Test
    void shouldCollapseConsecutiveHyphens() {
        String result = SlugGenerator.normalize("test---user");
        assertThat(result).isEqualTo("test-user");
    }

    @Test
    void shouldRemoveNonAsciiCharacters() {
        String result = SlugGenerator.normalize("홍길동");
        assertThat(result).isNotEmpty();
        // After removing non-ASCII, should be reasonable
        assertThat(result).doesNotContain("홍").doesNotContain("길").doesNotContain("동");
    }

    @Test
    void shouldHandleKoreanNickname() {
        String result = SlugGenerator.normalize("사용자");
        // Should convert to something, not be empty
        assertThat(result).isNotBlank();
        // Should only contain alphanumeric and hyphens
        assertThat(result).matches("[a-z0-9-]+");
    }

    @Test
    void shouldPadMinimumLengthWithFallback() {
        String result = SlugGenerator.normalize("ab");
        assertThat(result).isEqualTo("blog");
        assertThat(result).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void shouldUseFallbackForEmptyInput() {
        String result = SlugGenerator.normalize("");
        assertThat(result).isEqualTo("blog");
    }

    @Test
    void shouldUseFallbackForBlankInput() {
        String result = SlugGenerator.normalize("   ");
        assertThat(result).isEqualTo("blog");
    }

    @Test
    void shouldUseFallbackForNullInput() {
        String result = SlugGenerator.normalize(null);
        assertThat(result).isEqualTo("blog");
    }

    @Test
    void shouldTruncateExcessiveLength() {
        String result = SlugGenerator.normalize("verylongusernamethatshouldbetruncated");
        assertThat(result).hasSizeLessThanOrEqualTo(30);
    }

    @Test
    void shouldAvoidReservedWord() {
        String result = SlugGenerator.normalize("admin");
        assertThat(result).isNotEqualTo("admin");
        assertThat(result).contains("admin");
    }

    @Test
    void shouldAvoidMultipleReservedWords() {
        assertThat(SlugGenerator.normalize("api")).isNotEqualTo("api");
        assertThat(SlugGenerator.normalize("signin")).isNotEqualTo("signin");
        assertThat(SlugGenerator.normalize("signup")).isNotEqualTo("signup");
        assertThat(SlugGenerator.normalize("settings")).isNotEqualTo("settings");
        assertThat(SlugGenerator.normalize("write")).isNotEqualTo("write");
        assertThat(SlugGenerator.normalize("search")).isNotEqualTo("search");
    }

    @Test
    void shouldGenerateUniqueWithNoDuplicate() {
        String slug = SlugGenerator.generateUnique("testuser", s -> false);
        assertThat(slug).isEqualTo("testuser");
    }

    @Test
    void shouldAddSuffixForDuplicate() {
        String slug = SlugGenerator.generateUnique("testuser", s -> s.equals("testuser"));
        assertThat(slug).isEqualTo("testuser-2");
    }

    @Test
    void shouldIncrementSuffixForMultipleDuplicates() {
        String slug = SlugGenerator.generateUnique("testuser", s ->
            s.equals("testuser") || s.equals("testuser-2") || s.equals("testuser-3"));
        assertThat(slug).isEqualTo("testuser-4");
    }

    @Test
    void shouldHandleSpecialCharInDuplicateGeneration() {
        String slug = SlugGenerator.generateUnique("test@user!", s -> s.equals("test-user"));
        assertThat(slug).isEqualTo("test-user-2");
    }

    @Test
    void shouldApplyNormalizationBeforeDuplicateCheck() {
        // Both "TestUser" and "test-user" should map to the same base slug
        String slug = SlugGenerator.generateUnique("TestUser", s -> s.equals("testuser"));
        assertThat(slug).isEqualTo("testuser-2");
    }
}
