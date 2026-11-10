package com.zeroverse.domain.notification.service;

import com.zeroverse.domain.notification.entity.Notification;
import com.zeroverse.domain.notification.entity.NotificationTargetType;
import com.zeroverse.domain.notification.entity.NotificationType;
import com.zeroverse.domain.notification.repository.NotificationRepository;
import com.zeroverse.domain.user.entity.User;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Task3 원행위 transaction이 사용하는 알림 저장 경계.
 *
 * <p>이 클래스는 수신자·ACL·중복 정책을 결정하지 않는다. 호출자가 잠금 후 확정한 전체 수신자 집합을
 * {@link #createBatch(List)}로 넘기면 repository batch 저장과 flush만 수행한다.
 */
@Component
public class NotificationGenerator {

    private final NotificationRepository notificationRepository;

    public NotificationGenerator(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** 단일 알림 저장 계약(테스트·0/1 fan-out 경계). */
    public Notification create(
            User receiver,
            User actor,
            NotificationType type,
            NotificationTargetType targetType,
            Long targetId,
            String message) {
        return notificationRepository.save(Notification.create(
                receiver, actor, type, targetType, targetId, message));
    }

    /** 전체 수신자를 임의로 자르지 않는 batch 저장 계약. */
    public List<Notification> createBatch(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return List.of();
        }
        List<Notification> saved = new ArrayList<>(notifications.size());
        for (int from = 0; from < notifications.size(); from += 100) {
            int to = Math.min(from + 100, notifications.size());
            saved.addAll(notificationRepository.saveAll(notifications.subList(from, to)));
            notificationRepository.flush();
        }
        return List.copyOf(saved);
    }

    /** 원행위 응답 전에 FK/validation 오류를 같은 transaction에서 드러낸다. */
    public void flush() {
        notificationRepository.flush();
    }
}
