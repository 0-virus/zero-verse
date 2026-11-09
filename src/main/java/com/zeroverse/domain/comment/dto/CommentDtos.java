package com.zeroverse.domain.comment.dto;

import com.zeroverse.common.response.PageResponse;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/** M6 댓글 API DTO. 저장 HTML/Markdown 변환 없이 plain text만 다룬다. */
public final class CommentDtos {

    private CommentDtos() {}

    public record CreateCommentRequest(@NotNull String content, Long parentId) {}

    public record UpdateCommentRequest(@NotNull String content) {}

    public record CommentAuthor(Long id, String nickname, String profileImageUrl) {}

    public record CommentItem(
            Long id,
            Long postId,
            Long parentId,
            CommentAuthor author,
            String content,
            Boolean deleted,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            Boolean canEdit,
            Boolean canDelete,
            Boolean canReply,
            PageResponse<CommentItem> replies) {}
}
