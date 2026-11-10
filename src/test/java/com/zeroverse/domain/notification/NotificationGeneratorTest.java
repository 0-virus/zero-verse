package com.zeroverse.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeroverse.domain.notification.entity.Notification;
import com.zeroverse.domain.notification.entity.NotificationTargetType;
import com.zeroverse.domain.notification.entity.NotificationType;
import com.zeroverse.domain.notification.repository.NotificationRepository;
import com.zeroverse.domain.notification.service.NotificationGenerator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationGeneratorTest {

    @Mock private NotificationRepository notificationRepository;
    @Captor private ArgumentCaptor<List<Notification>> batchCaptor;

    @Test
    @DisplayName("생성기 batch 저장은 100개 단위 전체 수신자를 flush한다")
    void savesAllRecipientsInHundredBatches() {
        List<Notification> notifications = java.util.stream.IntStream.range(0, 201)
                .mapToObj(index -> Notification.create(
                        null, null, NotificationType.LIKE, NotificationTargetType.POST, (long) index, "message"))
                .toList();
        when(notificationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationGenerator generator = new NotificationGenerator(notificationRepository);

        List<Notification> saved = generator.createBatch(notifications);

        assertThat(saved).hasSize(201);
        verify(notificationRepository, times(3)).saveAll(batchCaptor.capture());
        verify(notificationRepository, times(3)).flush();
        assertThat(batchCaptor.getAllValues()).extracting(List::size).containsExactly(100, 100, 1);
    }
}
