package com.zeroverse.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeroverse.domain.notification.entity.Notification;
import com.zeroverse.domain.notification.entity.NotificationTargetType;
import com.zeroverse.domain.notification.entity.NotificationType;
import com.zeroverse.domain.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationEntityTest {

    @Test
    @DisplayName("factory는 V1 receiver/actor/type/target/message를 보존하고 최초 readAt만 기록한다")
    void factoryAndFirstReadAtAreStable() {
        User receiver = User.register("receiver@entity.test", "hash", "Receiver", "receiver",
                LocalDate.of(1990, 1, 1));
        User actor = User.register("actor@entity.test", "hash", "Actor", "actor",
                LocalDate.of(1990, 1, 1));
        Notification notification = Notification.create(
                receiver, actor, NotificationType.COMMENT, NotificationTargetType.COMMENT,
                42L, "stored");
        LocalDateTime first = LocalDateTime.of(2026, 11, 10, 1, 2, 3);
        notification.markRead(first);
        notification.markRead(first.plusDays(1));

        assertThat(notification.getReceiver()).isSameAs(receiver);
        assertThat(notification.getActor()).isSameAs(actor);
        assertThat(notification.getType()).isEqualTo(NotificationType.COMMENT);
        assertThat(notification.getTargetType()).isEqualTo(NotificationTargetType.COMMENT);
        assertThat(notification.getTargetId()).isEqualTo(42L);
        assertThat(notification.getMessage()).isEqualTo("stored");
        assertThat(notification.getReadAt()).isEqualTo(first);
        assertThat(notification.isDeleted()).isFalse();
    }
}
