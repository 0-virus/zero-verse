package com.zeroverse.dto.post;

import com.zeroverse.domain.post.entity.Post;

import java.util.List;

public record PostImagesResponse(
    Long postId,
    List<PostImageResponse> images
) {
    public static PostImagesResponse from(Post post) {
        // Set 컬렉션은 순서를 보장하지 않으므로 soft-delete 제외 후 displayOrder로 정렬한다
        List<PostImageResponse> images = post.getImages().stream()
            .filter(img -> !img.isDeleted())
            .sorted(java.util.Comparator.comparing(com.zeroverse.domain.post.entity.PostImage::getDisplayOrder))
            .map(PostImageResponse::from)
            .toList();

        return new PostImagesResponse(post.getId(), images);
    }
}
