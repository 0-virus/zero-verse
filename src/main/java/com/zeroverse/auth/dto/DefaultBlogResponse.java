package com.zeroverse.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zeroverse.domain.blog.entity.Blog;

public record DefaultBlogResponse(
    Long id,
    String title,
    @JsonProperty("urlSlug")
    String urlSlug,
    @JsonProperty("isSetupCompleted")
    Boolean isSetupCompleted
) {
    public static DefaultBlogResponse of(Blog blog) {
        return new DefaultBlogResponse(
            blog.getId(),
            blog.getTitle(),
            blog.getUrlSlug(),
            blog.getIsSetupCompleted()
        );
    }
}
