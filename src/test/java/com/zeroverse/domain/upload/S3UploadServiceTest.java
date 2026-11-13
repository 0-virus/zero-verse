package com.zeroverse.domain.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.post.repository.PostImageRepository;
import com.zeroverse.domain.post.service.PostAccessPolicy;
import com.zeroverse.domain.upload.config.S3StorageProperties;
import com.zeroverse.domain.upload.config.UploadProperties;
import com.zeroverse.domain.upload.dto.UploadDtos.PresignedUploadRequest;
import com.zeroverse.domain.upload.dto.UploadDtos.PresignedUploadResponse;
import com.zeroverse.domain.upload.entity.UploadIntent;
import com.zeroverse.domain.upload.repository.ImageUploadRepository;
import com.zeroverse.domain.upload.repository.UploadIntentRepository;
import com.zeroverse.domain.upload.service.S3ObjectStore;
import com.zeroverse.domain.upload.service.S3UploadService;
import com.zeroverse.domain.upload.service.UploadChecksum;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class S3UploadServiceTest {

    private static final byte[] JPEG = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
        0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0,
        (byte) 0xFF, (byte) 0xD9
    };

    private ImageUploadRepository imageUploadRepository;
    private UploadIntentRepository uploadIntentRepository;
    private UserRepository userRepository;
    private S3ObjectStore objectStore;
    private S3StorageProperties s3Properties;
    private UploadProperties uploadProperties;
    private User owner;
    private Clock clock;
    private S3UploadService service;

    @BeforeEach
    void setUp() {
        imageUploadRepository = mock(ImageUploadRepository.class);
        uploadIntentRepository = mock(UploadIntentRepository.class);
        userRepository = mock(UserRepository.class);
        objectStore = mock(S3ObjectStore.class);
        PostAccessPolicy postAccessPolicy = mock(PostAccessPolicy.class);
        PostImageRepository postImageRepository = mock(PostImageRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        s3Properties = new S3StorageProperties();
        s3Properties.setBucket("zero-verse-s3");
        s3Properties.setRegion("ap-northeast-2");
        s3Properties.setPrefix("zeroverse/dev/test/");
        uploadProperties = new UploadProperties(".local-data/uploads");
        uploadProperties.setBackend("s3");
        clock = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC);
        owner = mock(User.class);
        when(owner.getId()).thenReturn(7L);
        when(owner.isActive()).thenReturn(true);
        when(owner.isDeleted()).thenReturn(false);
        when(userRepository.findByIdAndDeletedAtIsNull(anyLong())).thenReturn(Optional.of(owner));
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(anyLong())).thenReturn(Optional.of(owner));
        service = new S3UploadService(
                imageUploadRepository,
                uploadIntentRepository,
                userRepository,
                objectStore,
                s3Properties,
                uploadProperties,
                postAccessPolicy,
                postImageRepository,
                entityManager,
                clock);
    }

    @Test
    void presignPersistsServerOwnedLocationAndReturnsOnlySignedHeaders() {
        String checksum = UploadChecksum.sha256Base64(JPEG);
        when(uploadIntentRepository.saveAndFlush(any(UploadIntent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(objectStore.presignPut(any(), any(), any(), anyLong(), any()))
                .thenReturn(new S3ObjectStore.PresignedPut(
                        "https://signed.example/opaque", java.util.Map.of(
                                "Content-Type", "image/png",
                                "x-amz-checksum-sha256", checksum,
                                "If-None-Match", "*")));

        PresignedUploadResponse response = service.presign(
                7L,
                new PresignedUploadRequest(
                        "avatar.jpg", "image/jpeg", (long) JPEG.length,
                        UploadPurpose.PROFILE_IMAGE, checksum));

        assertThat(response.method()).isEqualTo("PUT");
        assertThat(response.uploadUrl()).isEqualTo("https://signed.example/opaque");
        assertThat(response.headers()).containsEntry("If-None-Match", "*");
        assertThat(response.id()).isNotNull();
        var captured = org.mockito.ArgumentCaptor.forClass(UploadIntent.class);
        verify(uploadIntentRepository).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getObjectKey())
                .isEqualTo("zeroverse/dev/test/" + response.id());
        assertThat(captured.getValue().getExpectedSha256()).isEqualTo(checksum);
    }

    @Test
    void missingObjectRemainsPendingAndMapsToConflict() {
        UUID id = UUID.randomUUID();
        String checksum = UploadChecksum.sha256Base64(JPEG);
        UploadIntent intent = UploadIntent.pending(
                id, owner, UploadPurpose.POST_IMAGE, JPEG.length, "image/jpeg", checksum,
                "zero-verse-s3", "ap-northeast-2", "zeroverse/dev/test/" + id,
                java.time.LocalDateTime.now(clock).plusMinutes(5),
                java.time.LocalDateTime.now(clock).plusHours(24));
        when(uploadIntentRepository.findByIdForUpdate(id)).thenReturn(Optional.of(intent));
        doThrow(new S3ObjectStore.ObjectMissingException(true))
                .when(objectStore).head(any(), any(), any());

        assertThatThrownBy(() -> service.complete(7L, id))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.UPLOAD_005);
        assertThat(intent.getStatus()).isEqualTo(UploadIntentStatus.PENDING);
        verify(imageUploadRepository, never()).saveAndFlush(any());
    }

    @Test
    void checksumMismatchDoesNotCreateImageMetadata() {
        UUID id = UUID.randomUUID();
        String expected = UploadChecksum.sha256Base64("different".getBytes());
        UploadIntent intent = UploadIntent.pending(
                id, owner, UploadPurpose.POST_IMAGE, JPEG.length, "image/jpeg", expected,
                "zero-verse-s3", "ap-northeast-2", "zeroverse/dev/test/" + id,
                java.time.LocalDateTime.now(clock).plusMinutes(5),
                java.time.LocalDateTime.now(clock).plusHours(24));
        when(uploadIntentRepository.findByIdForUpdate(id)).thenReturn(Optional.of(intent));
        when(objectStore.get(any(), any(), any()))
                .thenReturn(new S3ObjectStore.ObjectSnapshot(JPEG, "image/jpeg"));

        assertThatThrownBy(() -> service.complete(7L, id))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.UPLOAD_007);
        verify(imageUploadRepository, never()).saveAndFlush(any());
    }
}
