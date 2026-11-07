package com.zeroverse.domain.universe.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import java.time.LocalDateTime;

/** 유니버스 API의 최소 개인정보 DTO 모음이다. 실명·이메일·생년월일·소개는 포함하지 않는다. */
public final class UniverseDtos {

    private UniverseDtos() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserSummary(Long id, String nickname, String profileImageUrl, String blogSlug) {}

    public record UniverseRequestResponse(Long requestId, UserSummary user,
            UniverseStatus status, LocalDateTime createdAt) {}

    public record UniverseRequestItem(Long requestId, UserSummary user, LocalDateTime createdAt) {}

    public record UniverseRelationItem(UserSummary user, LocalDateTime updatedAt) {}

    public enum Availability { AVAILABLE, BLOCKED_BY_ME, UNAVAILABLE }

    public enum RelationshipStatus { NONE, PENDING, ACCEPTED }

    /** 방향별 관계를 보존한다. requestId는 PENDING에서만 노출한다. */
    public record DirectionalRelation(RelationshipStatus status, Long requestId) {
        public DirectionalRelation {
            if (status != RelationshipStatus.PENDING) {
                requestId = null;
            }
        }

        public static DirectionalRelation none() {
            return new DirectionalRelation(RelationshipStatus.NONE, null);
        }
    }

    public record RelationshipSnapshotTarget(Long id, String nickname, String profileImageUrl,
            String blogSlug) {}

    public record UniverseRelationshipSnapshot(RelationshipSnapshotTarget target,
            Availability availability, DirectionalRelation outgoing, DirectionalRelation incoming) {}
}
