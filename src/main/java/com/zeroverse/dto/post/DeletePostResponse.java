package com.zeroverse.dto.post;

import java.time.LocalDateTime;

public record DeletePostResponse(
    Long postId,
    LocalDateTime deletedAt
) {
}
