package com.zeroverse.domain.notification.entity;

import com.zeroverse.common.entity.BaseSoftDeleteEntity;
import com.zeroverse.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.DynamicUpdate;

/** V1 notifications row. 생성 연계는 M8 Task3가 이 엔티티의 factory를 사용한다. */
@Entity
@DynamicUpdate
@Table(name = "notifications")
public class Notification extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_user_id", nullable = false)
    private User receiver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private NotificationTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected Notification() {}

    private Notification(
            User receiver,
            User actor,
            NotificationType type,
            NotificationTargetType targetType,
            Long targetId,
            String message) {
        this.receiver = receiver;
        this.actor = actor;
        this.type = type;
        this.targetType = targetType;
        this.targetId = targetId;
        this.message = message;
    }

    /** Task3 생성기가 원행위 transaction 안에서 사용할 canonical row factory. */
    public static Notification create(
            User receiver,
            User actor,
            NotificationType type,
            NotificationTargetType targetType,
            Long targetId,
            String message) {
        return new Notification(receiver, actor, type, targetType, targetId, message);
    }

    /** 최초 읽음 시각을 보존한다. */
    public void markRead(LocalDateTime at) {
        if (readAt == null) {
            readAt = at == null ? LocalDateTime.now() : at;
        }
    }

    public Long getId() { return id; }
    public User getReceiver() { return receiver; }
    public User getActor() { return actor; }
    public NotificationType getType() { return type; }
    public NotificationTargetType getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getMessage() { return message; }
    public LocalDateTime getReadAt() { return readAt; }
}
