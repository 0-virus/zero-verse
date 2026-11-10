package com.zeroverse.domain.notification.dto;

import com.zeroverse.domain.notification.entity.NotificationTargetType;
import com.zeroverse.domain.notification.entity.NotificationType;
import java.time.OffsetDateTime;
import java.util.List;

/** M8 알림 API 응답 DTO. */
public final class NotificationDtos {

    private NotificationDtos() {}

    public enum ReadFilter {
        ALL,
        UNREAD,
        READ;

        public static ReadFilter fromQuery(String value) {
            if (value == null) {
                return ALL;
            }
            return switch (value) {
                case "all" -> ALL;
                case "unread" -> UNREAD;
                case "read" -> READ;
                default -> throw new IllegalArgumentException("read 값이 올바르지 않습니다.");
            };
        }

        public String queryValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record NotificationActor(Long id, String nickname, String profileImageUrl) {}

    public record NotificationItem(
            Long id,
            NotificationType type,
            String message,
            NotificationActor actor,
            NotificationTargetType targetType,
            Long targetId,
            String href,
            Boolean available,
            OffsetDateTime readAt,
            OffsetDateTime createdAt) {}

    /** PageResponse 7개 필드 + 전체 visible 미읽음 수 + 현재 viewer 관리 가능 여부. */
    public record NotificationPage(
            List<NotificationItem> items,
            Integer page,
            Integer size,
            Long totalElements,
            Integer totalPages,
            Boolean hasNext,
            Boolean hasPrevious,
            Long unreadCount,
            Boolean canManage) {
        public NotificationPage {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
