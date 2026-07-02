package com.zeroverse.dto.blog;

public record BlogSettingsRequest(
    String title,
    String urlSlug,
    String description
) {
}
