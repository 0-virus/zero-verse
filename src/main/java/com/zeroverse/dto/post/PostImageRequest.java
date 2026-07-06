package com.zeroverse.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PostImageRequest(
    @NotBlank(message = "imageUrl은 필수입니다.")
    String imageUrl,

    String altText,

    @NotNull(message = "displayOrder는 필수입니다.")
    Integer displayOrder
) {
}
