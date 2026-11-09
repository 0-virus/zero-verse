package com.zeroverse.domain.like.dto;

/** M6 게시글 interactions 상태 DTO. */
public final class LikeDtos {

    private LikeDtos() {}

    public record InteractionState(
            Long postId,
            Long commentCount,
            Long likeCount,
            Boolean liked,
            Boolean canComment,
            Boolean canLike,
            Boolean canUnlike) {}
}
