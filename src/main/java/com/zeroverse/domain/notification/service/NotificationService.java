package com.zeroverse.domain.notification.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.notification.dto.NotificationDtos.NotificationActor;
import com.zeroverse.domain.notification.dto.NotificationDtos.NotificationItem;
import com.zeroverse.domain.notification.dto.NotificationDtos.NotificationPage;
import com.zeroverse.domain.notification.dto.NotificationDtos.ReadFilter;
import com.zeroverse.domain.notification.entity.NotificationTargetType;
import com.zeroverse.domain.notification.entity.NotificationType;
import com.zeroverse.domain.notification.repository.NotificationRepository;
import com.zeroverse.domain.notification.repository.NotificationRepository.NotificationRow;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.NotificationViewerResolver;
import com.zeroverse.security.NotificationViewerResolver.Viewer;
import com.zeroverse.security.ZeroverseUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** M8 알림 목록·읽음·전체읽음·soft delete 서비스. */
@Service
public class NotificationService {

    private static final int DEFAULT_PAGE = 0;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String UNAVAILABLE_MESSAGE = "더 이상 확인할 수 없는 알림입니다";
    private static final String NEIGHBOR_REQUEST_MESSAGE = "유니버스 신청을 받았습니다.";
    private static final String NEIGHBOR_ACCEPT_MESSAGE = "유니버스 신청이 수락되었습니다.";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationViewerResolver viewerResolver;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            NotificationViewerResolver viewerResolver) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.viewerResolver = viewerResolver;
    }

    /** 목록은 visible count/items/unread를 같은 RR snapshot에서 계산한다. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public NotificationPage list(
            HttpServletRequest request,
            ZeroverseUserPrincipal principal,
            ReadFilter readFilter,
            int page,
            int size) {
        Viewer viewer = viewerResolver.resolve(request, principal);
        validatePage(page, size);
        ReadFilter effectiveFilter = readFilter == null ? ReadFilter.ALL : readFilter;
        NotificationRepository.VisibleCount count = notificationRepository.countVisible(
                viewer.userId(), effectiveFilter.queryValue());
        long total = valueOrZero(count == null ? null : count.getTotalElements());
        long unread = valueOrZero(count == null ? null : count.getUnreadCount());
        long offset = (long) page * size;
        if (offset >= total || offset > Integer.MAX_VALUE) {
            return page(List.of(), page, size, total, unread, viewer.canManage());
        }
        List<NotificationRow> rows = notificationRepository.findVisible(
                viewer.userId(), effectiveFilter.queryValue(), size, offset);
        List<NotificationItem> items = rows.stream()
                .map(this::toItem)
                .toList();
        return page(items, page, size, total, unread, viewer.canManage());
    }

    /** 개별 read는 최초 readAt을 보존하고 현재 projection을 반환한다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public NotificationItem read(
            HttpServletRequest request,
            ZeroverseUserPrincipal principal,
            Long notificationId) {
        Viewer viewer = viewerResolver.resolve(request, principal);
        User receiver = lockActiveReceiver(viewer.userId());
        com.zeroverse.domain.notification.entity.Notification notification = notificationRepository
                .findByIdForUpdate(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_001));
        verifyOwner(receiver, notification);
        if (notification.isDeleted()) {
            throw new BusinessException(ErrorCode.NOT_001);
        }
        rejectBlocked(viewer.userId(), notification.getActor() == null
                ? null : notification.getActor().getId());
        notification.markRead(LocalDateTime.now());
        notificationRepository.flush();
        return notificationRepository.findOwnedCurrentRow(viewer.userId(), notificationId)
                .map(this::toItem)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_001));
    }

    /** 전체 읽음은 receiver lock 뒤 visible unread만 한 UPDATE statement로 처리한다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void readAll(HttpServletRequest request, ZeroverseUserPrincipal principal) {
        Viewer viewer = viewerResolver.resolve(request, principal);
        lockActiveReceiver(viewer.userId());
        notificationRepository.markAllVisibleUnread(viewer.userId(), LocalDateTime.now());
    }

    /** 자기 알림만 soft delete하며 이미 삭제한 자기 row는 멱등 empty success다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(
            HttpServletRequest request,
            ZeroverseUserPrincipal principal,
            Long notificationId) {
        Viewer viewer = viewerResolver.resolve(request, principal);
        User receiver = lockActiveReceiver(viewer.userId());
        com.zeroverse.domain.notification.entity.Notification notification = notificationRepository
                .findByIdForUpdate(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_001));
        verifyOwner(receiver, notification);
        if (notification.isDeleted()) {
            return;
        }
        rejectBlocked(viewer.userId(), notification.getActor() == null
                ? null : notification.getActor().getId());
        notification.softDelete();
        notificationRepository.flush();
    }

    private User lockActiveReceiver(Long viewerId) {
        User receiver = userRepository.findByIdAndDeletedAtIsNullForUpdate(viewerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        if (!receiver.isActive()) {
            throw new BusinessException(ErrorCode.USER_003);
        }
        return receiver;
    }

    private static void verifyOwner(
            User receiver,
            com.zeroverse.domain.notification.entity.Notification notification) {
        if (!Objects.equals(receiver.getId(), notification.getReceiver().getId())) {
            throw new BusinessException(ErrorCode.NOT_002);
        }
    }

    private void rejectBlocked(Long receiverId, Long actorId) {
        if (actorId != null && Long.valueOf(1L).equals(
                notificationRepository.existsBlockedBetween(receiverId, actorId))) {
            throw new BusinessException(ErrorCode.NOT_001);
        }
    }

    private NotificationItem toItem(NotificationRow row) {
        NotificationType type = parseType(row.getNotificationType());
        NotificationTargetType targetType = parseTargetType(row.getNotificationTargetType());
        boolean actorAvailable = row.getActorId() != null && row.getActorDeletedAt() == null;
        boolean targetAvailable = switch (targetType == null ? "" : targetType.name()) {
            case "POST" -> isTrue(row.getPostAccessible());
            case "COMMENT" -> isTrue(row.getCommentAccessible());
            case "UNIVERSE" -> isTrue(row.getUniverseAccessible());
            default -> false;
        };
        boolean supportedPair = supportedPair(type, targetType);
        boolean neighborMessageValid = type != NotificationType.NEIGHBOR
                || NEIGHBOR_REQUEST_MESSAGE.equals(row.getStoredMessage())
                || NEIGHBOR_ACCEPT_MESSAGE.equals(row.getStoredMessage());
        boolean available = actorAvailable && targetAvailable && supportedPair && neighborMessageValid;
        OffsetDateTime readAt = toUtc(row.getReadAt());
        OffsetDateTime createdAt = toUtc(row.getCreatedAt());
        if (!available) {
            return new NotificationItem(
                    row.getId(), type, UNAVAILABLE_MESSAGE, null, targetType, null, null, false,
                    readAt, createdAt);
        }
        NotificationActor actor = new NotificationActor(
                row.getActorId(), row.getActorNickname(), row.getActorProfileImageUrl());
        Long targetId = row.getNotificationTargetId();
        String href = href(type, targetType, row);
        return new NotificationItem(
                row.getId(), type, safeMessage(type, row.getStoredMessage(), row.getActorNickname()), actor,
                targetType, targetId, href, true, readAt, createdAt);
    }

    private static String safeMessage(NotificationType type, String stored, String nickname) {
        String actor = nickname == null ? "누군가" : nickname;
        return switch (type) {
            case COMMENT -> actor + "님이 댓글을 남겼습니다.";
            case REPLY -> actor + "님이 답글을 남겼습니다.";
            case LIKE -> actor + "님이 회원님의 글을 좋아합니다.";
            case POST -> actor + "님이 새 글을 발행했습니다.";
            case NEIGHBOR -> stored;
        };
    }

    private static String href(NotificationType type, NotificationTargetType targetType, NotificationRow row) {
        if (type == NotificationType.NEIGHBOR && targetType == NotificationTargetType.UNIVERSE) {
            return "/settings/universe";
        }
        if (targetType == NotificationTargetType.POST && row.getPostId() != null
                && row.getPostBlogSlug() != null) {
            return "/blog/" + row.getPostBlogSlug() + "/" + row.getPostId();
        }
        if (targetType == NotificationTargetType.COMMENT && row.getCommentPostId() != null
                && row.getPostBlogSlug() != null) {
            return "/blog/" + row.getPostBlogSlug() + "/" + row.getCommentPostId();
        }
        return null;
    }

    private static NotificationType parseType(String raw) {
        if (raw == null) return null;
        try {
            return NotificationType.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static NotificationTargetType parseTargetType(String raw) {
        if (raw == null) return null;
        try {
            return NotificationTargetType.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean supportedPair(NotificationType type, NotificationTargetType targetType) {
        return (type == NotificationType.COMMENT || type == NotificationType.REPLY)
                && targetType == NotificationTargetType.COMMENT
                || (type == NotificationType.LIKE || type == NotificationType.POST)
                && targetType == NotificationTargetType.POST
                || type == NotificationType.NEIGHBOR
                && targetType == NotificationTargetType.UNIVERSE;
    }

    private static boolean isTrue(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private static NotificationPage page(
            List<NotificationItem> items,
            int page,
            int size,
            long total,
            long unread,
            boolean canManage) {
        long offset = (long) page * size;
        long end = offset + items.size();
        return new NotificationPage(
                items, page, size, total, totalPages(total, size), end < total, page > 0, unread, canManage);
    }

    private static int totalPages(long total, int size) {
        long pages = total == 0 ? 0 : (total + size - 1L) / size;
        return (int) Math.min(Integer.MAX_VALUE, pages);
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private static void validatePage(int page, int size) {
        if (page < DEFAULT_PAGE || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "페이지 값이 올바르지 않습니다.");
        }
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null
                : value.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
    }
}
