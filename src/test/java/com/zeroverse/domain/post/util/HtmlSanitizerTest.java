package com.zeroverse.domain.post.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HtmlSanitizer 테스트")
class HtmlSanitizerTest {

    @Test
    @DisplayName("허용 태그(p, strong, em, ul, ol, li, a, img 등)만 유지한다")
    void testAllowedTags() {
        String input = "<p>Hello <strong>world</strong> <em>test</em></p>";
        String sanitized = HtmlSanitizer.sanitize(input);

        assertThat(sanitized).contains("<p>", "<strong>", "<em>", "</p>", "</strong>", "</em>");
        assertThat(sanitized).doesNotContain("<script>");
    }

    @Test
    @DisplayName("script 태그를 제거한다")
    void testRemoveScript() {
        String input = "<p>Hello</p><script>alert('xss')</script><p>World</p>";
        String sanitized = HtmlSanitizer.sanitize(input);

        assertThat(sanitized).doesNotContain("<script>")
            .doesNotContain("alert");
    }

    @Test
    @DisplayName("inline event handler를 제거한다")
    void testRemoveEventHandler() {
        String input = "<img src='test.jpg' onerror='alert(1)' alt='test'/>";
        String sanitized = HtmlSanitizer.sanitize(input);

        assertThat(sanitized).doesNotContain("onerror")
            .doesNotContain("alert");
    }

    @Test
    @DisplayName("javascript: URL을 제거한다")
    void testRemoveJavascriptUrl() {
        String input = "<a href='javascript:alert(1)'>Click</a>";
        String sanitized = HtmlSanitizer.sanitize(input);

        assertThat(sanitized).doesNotContain("javascript:")
            .doesNotContain("alert");
    }

    @Test
    @DisplayName("h1, h2, h3 태그를 허용한다")
    void testAllowHeadings() {
        String input = "<h1>Title</h1><h2>Subtitle</h2><h3>Section</h3>";
        String sanitized = HtmlSanitizer.sanitize(input);

        assertThat(sanitized).contains("<h1>", "<h2>", "<h3>");
    }

    @Test
    @DisplayName("blockquote, pre, code 태그를 허용한다")
    void testAllowCodeBlocks() {
        String input = "<blockquote>Quote</blockquote><pre>Code</pre><code>inline</code>";
        String sanitized = HtmlSanitizer.sanitize(input);

        assertThat(sanitized).contains("<blockquote>", "<pre>", "<code>");
    }

    @Test
    @DisplayName("ul, ol, li 태그를 허용한다")
    void testAllowLists() {
        String input = "<ul><li>Item 1</li><li>Item 2</li></ul>";
        String sanitized = HtmlSanitizer.sanitize(input);

        assertThat(sanitized).contains("<ul>", "<li>", "</li>", "</ul>");
    }

    @Test
    @DisplayName("a 태그의 href 속성을 허용한다")
    void testAllowAnchorHref() {
        String input = "<a href='https://example.com'>Link</a>";
        String sanitized = HtmlSanitizer.sanitize(input);

        assertThat(sanitized).contains("href=", "https://example.com");
    }

    @Test
    @DisplayName("img 태그의 src, alt 속성을 허용한다")
    void testAllowImageAttributes() {
        String input = "<img src='https://example.com/image.jpg' alt='test image'/>";
        String sanitized = HtmlSanitizer.sanitize(input);

        assertThat(sanitized).contains("src=", "https://example.com/image.jpg")
            .contains("alt=", "test image");
    }

    @Test
    @DisplayName("null 입력은 빈 문자열을 반환한다")
    void testNullInput() {
        String sanitized = HtmlSanitizer.sanitize(null);

        assertThat(sanitized).isEmpty();
    }

    @Test
    @DisplayName("복합 위험 콘텐츠를 제거한다")
    void testComplexXss() {
        String input = "<p>Safe <strong>content</strong></p>" +
            "<img src='x' onerror='alert(1)' alt='test'/>" +
            "<script>alert('xss')</script>" +
            "<a href='javascript:void(0)'>click</a>";
        String sanitized = HtmlSanitizer.sanitize(input);

        assertThat(sanitized).contains("<p>", "<strong>", "Safe")
            .doesNotContain("onerror", "alert", "<script>", "javascript:");
    }
}
