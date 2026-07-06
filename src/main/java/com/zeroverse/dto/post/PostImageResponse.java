package com.zeroverse.dto.post;

import com.zeroverse.domain.post.entity.PostImage;

public record PostImageResponse(
    Long postImageId,
    String imageUrl,
    String altText,
    Integer displayOrder
) {
    public static PostImageResponse from(PostImage image) {
        return new PostImageResponse(
            image.getId(),
            image.getImageUrl(),
            image.getAltText(),
            image.getDisplayOrder()
        );
    }
}
