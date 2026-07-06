package com.zeroverse.dto.post;

import com.zeroverse.domain.post.entity.Tag;

public record TagResponse(
    Long tagId,
    String name,
    String normalizedName
) {
    public static TagResponse from(Tag tag) {
        return new TagResponse(
            tag.getId(),
            tag.getName(),
            tag.getNormalizedName()
        );
    }
}
