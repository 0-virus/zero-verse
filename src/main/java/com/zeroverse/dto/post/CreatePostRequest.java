package com.zeroverse.dto.post;

import com.zeroverse.domain.post.entity.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreatePostRequest(
    Long blogId,

    Long categoryId,

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자 이내여야 합니다.")
    String title,

    @NotBlank(message = "contentJson은 필수입니다.")
    String contentJson,

    String contentHtml,

    String thumbnailUrl,

    @NotNull(message = "공개 범위는 필수입니다.")
    Visibility visibility,

    @NotNull(message = "publish 여부는 필수입니다.")
    Boolean publish,

    List<String> tagNames,

    List<PostImageRequest> images
) {
}
