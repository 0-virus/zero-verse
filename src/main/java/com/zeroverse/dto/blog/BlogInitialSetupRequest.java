package com.zeroverse.dto.blog;

public record BlogInitialSetupRequest(
    String title,
    String urlSlug,
    String description
) {
}
