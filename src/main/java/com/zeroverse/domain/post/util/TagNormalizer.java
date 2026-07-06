package com.zeroverse.domain.post.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 태그 정규화: trim + lowercase + 중복 제거
 */
public class TagNormalizer {

    public static List<String> normalize(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> normalized = new HashSet<>();
        for (String tag : tagNames) {
            if (tag == null) {
                continue;
            }
            String trimmed = tag.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }

        return new ArrayList<>(normalized);
    }

    public static String normalizeSingle(String tag) {
        if (tag == null || tag.isEmpty()) {
            return "";
        }
        return tag.trim().toLowerCase();
    }
}
