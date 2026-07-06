package com.zeroverse.domain.post.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * HTML 콘텐츠 sanitization: 허용된 태그와 속성만 유지
 * 허용 태그: p, br, strong, em, ul, ol, li, blockquote, pre, code, a[href], img[src|alt], h1-h3
 * script, inline event handler, javascript: URL 제거
 */
public class HtmlSanitizer {

    private static final Safelist SAFELIST = Safelist.basic()
        .addTags("h1", "h2", "h3", "blockquote", "pre", "code")
        .addAttributes("a", "href")
        .addAttributes("img", "src", "alt");

    public static String sanitize(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        return Jsoup.clean(html, SAFELIST);
    }
}
