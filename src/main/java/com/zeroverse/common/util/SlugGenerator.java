package com.zeroverse.common.util;

import java.text.Normalizer;
import java.util.Set;

/**
 * Generates URL-safe slugs from user input following PRD §4.5 rules.
 * - Lowercase alphanumeric and hyphens only
 * - Remove leading/trailing/consecutive hyphens
 * - Enforce 3-30 character length
 * - Avoid reserved words (admin, api, signin, signup, settings, write, edit, search)
 * - Fallback to 'blog' if empty after normalization
 */
public class SlugGenerator {
    private static final Set<String> RESERVED_WORDS = Set.of(
        "admin", "api", "signin", "signup", "settings", "write", "edit", "search"
    );
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 30;
    private static final String FALLBACK = "blog";

    /**
     * Normalize input to a valid slug.
     *
     * @param input Raw user input (nickname, etc.)
     * @return Normalized slug (3-30 chars, alphanumeric/hyphen, no reserved words)
     */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return FALLBACK;
        }

        // Step 1: Normalize Unicode and convert to lowercase
        String slug = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replaceAll("[^\\p{ASCII}]", "") // Remove non-ASCII after decomposition
            .toLowerCase();

        // Step 2: Replace invalid characters with hyphens
        slug = slug.replaceAll("[^a-z0-9-]", "-");

        // Step 3: Remove leading/trailing and consecutive hyphens
        slug = slug.replaceAll("^-+|-+$", ""); // Remove leading/trailing
        slug = slug.replaceAll("-+", "-"); // Replace consecutive hyphens with single

        // Step 4: Enforce minimum length (pad if needed)
        if (slug.isEmpty() || slug.length() < MIN_LENGTH) {
            slug = FALLBACK;
        }

        // Step 5: Truncate to maximum length
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", ""); // Also remove trailing hyphen
        }

        // Step 6: Check reserved words
        if (RESERVED_WORDS.contains(slug)) {
            slug = slug + "-blog";
        }

        // Re-enforce length after reserved word check
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }

        return slug;
    }

    /**
     * Generate a slug with collision suffix if needed.
     *
     * @param input Raw user input
     * @param existingSlugChecker Functional interface to check if a slug already exists
     * @return Unique slug with optional suffix (-2, -3, etc.)
     */
    public static String generateUnique(String input, SlugExistsChecker existingSlugChecker) {
        String baseSlug = normalize(input);

        if (!existingSlugChecker.exists(baseSlug)) {
            return baseSlug;
        }

        // Add suffix for collision
        for (int i = 2; i <= 100; i++) {
            String candidate = baseSlug + "-" + i;
            if (!existingSlugChecker.exists(candidate)) {
                return candidate;
            }
        }

        // Fallback (should rarely happen)
        return baseSlug + "-" + System.currentTimeMillis();
    }

    @FunctionalInterface
    public interface SlugExistsChecker {
        boolean exists(String slug);
    }
}
