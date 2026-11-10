package com.zeroverse.domain.notification.service;

import com.zeroverse.domain.comment.entity.Comment;
import com.zeroverse.domain.notification.entity.Notification;
import com.zeroverse.domain.notification.entity.NotificationTargetType;
import com.zeroverse.domain.notification.entity.NotificationType;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.post.service.PostAccessPolicy;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import com.zeroverse.domain.universe.repository.UniverseRepository;
import com.zeroverse.domain.user.entity.User;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Source-event policies; callers already hold every actor/receiver user lock. */
@Component
public class NotificationEventService {
    private final NotificationGenerator generator;
    private final PostAccessPolicy access;
    private final UniverseRepository universes;

    public NotificationEventService(
            NotificationGenerator generator, PostAccessPolicy access, UniverseRepository universes) {
        this.generator = generator;
        this.access = access;
        this.universes = universes;
    }

    public static boolean eligible(Post post) {
        return post.getPublishedAt() != null
                && (post.getVisibility() == Visibility.PUBLIC || post.getVisibility() == Visibility.UNIVERSE);
    }

    /** The publication lock already verified living receivers, current ACCEPTED and both blocks. */
    public void published(Post post, Collection<User> receivers) {
        generator.createBatch(receivers.stream()
                .map(receiver -> Notification.create(receiver, post.getUser(), NotificationType.POST,
                        NotificationTargetType.POST, post.getId(), "새 글이 발행되었습니다."))
                .toList());
    }

    public void comment(Comment comment) {
        Post post = comment.getPost();
        User actor = comment.getUser();
        Map<Long, Recipient> receivers = new LinkedHashMap<>();
        receivers.put(post.getUser().getId(), new Recipient(post.getUser(), NotificationType.COMMENT));
        if (comment.getParent() != null) {
            User parentAuthor = comment.getParent().getUser();
            receivers.put(parentAuthor.getId(), new Recipient(parentAuthor, NotificationType.REPLY));
        }
        List<Notification> notifications = new ArrayList<>();
        for (Recipient recipient : receivers.values()) {
            if (canReceive(recipient.user(), actor, post)) {
                notifications.add(Notification.create(recipient.user(), actor, recipient.type(),
                        NotificationTargetType.COMMENT, comment.getId(),
                        recipient.type() == NotificationType.REPLY ? "답글이 작성되었습니다." : "댓글이 작성되었습니다."));
            }
        }
        generator.createBatch(notifications);
    }

    public void like(Post post, User actor) {
        if (canReceive(post.getUser(), actor, post)) {
            generator.create(post.getUser(), actor, NotificationType.LIKE,
                    NotificationTargetType.POST, post.getId(), "좋아요를 받았습니다.");
            generator.flush();
        }
    }

    public void neighbor(User receiver, User actor, Long relationshipId, boolean accepted) {
        generator.create(receiver, actor, NotificationType.NEIGHBOR, NotificationTargetType.UNIVERSE,
                relationshipId, accepted ? "유니버스 신청이 수락되었습니다." : "유니버스 신청을 받았습니다.");
        generator.flush();
    }

    private boolean canReceive(User receiver, User actor, Post post) {
        if (receiver == null || receiver.isDeleted() || Objects.equals(receiver.getId(), actor.getId())
                || !access.canReadCurrent(post, receiver.getId())) {
            return false;
        }
        return universes.findPairForUpdate(
                        Math.min(receiver.getId(), actor.getId()), Math.max(receiver.getId(), actor.getId()))
                .stream().noneMatch(row -> row.getStatus() == UniverseStatus.BLOCKED);
    }

    private record Recipient(User user, NotificationType type) {}
}
