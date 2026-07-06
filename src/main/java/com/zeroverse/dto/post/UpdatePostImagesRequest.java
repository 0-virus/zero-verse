package com.zeroverse.dto.post;

import java.util.List;

public record UpdatePostImagesRequest(
    List<PostImageRequest> images
) {
}
