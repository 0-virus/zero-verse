package com.zeroverse.domain.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeroverse.domain.upload.entity.UploadIntent;
import com.zeroverse.domain.upload.repository.ImageUploadRepository;
import com.zeroverse.domain.upload.repository.UploadIntentRepository;
import com.zeroverse.domain.upload.service.S3ObjectStore;
import com.zeroverse.domain.upload.service.UploadIntentCleanupService;
import com.zeroverse.domain.user.entity.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class UploadIntentCleanupServiceTest {

    private UploadIntentRepository intentRepository;
    private ImageUploadRepository imageRepository;
    private S3ObjectStore objectStore;
    private UploadIntentCleanupService service;
    private UploadIntent intent;

    @BeforeEach
    void setUp() {
        intentRepository = mock(UploadIntentRepository.class);
        imageRepository = mock(ImageUploadRepository.class);
        objectStore = mock(S3ObjectStore.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        TransactionStatus txStatus = mock(TransactionStatus.class);
        when(txManager.getTransaction(any())).thenReturn(txStatus);
        User owner = mock(User.class);
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.ofInstant(
                Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC);
        intent = UploadIntent.pending(
                id,
                owner,
                UploadPurpose.POST_IMAGE,
                10,
                "image/jpeg",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "zero-verse-s3",
                "ap-northeast-2",
                "zeroverse/dev/test/" + id,
                now.minusMinutes(1),
                now.minusHours(1));
        when(intentRepository.findByStatusInAndExpiresAtLessThanEqualAndUploadExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                        any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(intent));
        when(intentRepository.findByIdForUpdate(id)).thenReturn(Optional.of(intent));
        when(imageRepository.existsById(id)).thenReturn(false);
        when(intentRepository.saveAndFlush(any(UploadIntent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service = new UploadIntentCleanupService(
                intentRepository,
                imageRepository,
                objectStore,
                Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC),
                txManager);
    }

    @Test
    void dryRunDoesNotChangeIntentOrCallS3() {
        UploadIntentCleanupService.CleanupResult result = service.cleanup(false);

        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.transitioned()).isZero();
        assertThat(intent.getStatus()).isEqualTo(UploadIntentStatus.PENDING);
        verify(objectStore, never()).delete(any(), any());
        verify(intentRepository, never()).saveAndFlush(any());
    }

    @Test
    void applyTransitionsThenDeletesAndLeavesTombstone() {
        UploadIntentCleanupService.CleanupResult result = service.cleanup(true);

        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.retried()).isZero();
        assertThat(intent.getStatus()).isEqualTo(UploadIntentStatus.DELETED);
        verify(objectStore).delete(
                "ap-northeast-2", "zero-verse-s3", intent.getObjectKey());
    }
}
