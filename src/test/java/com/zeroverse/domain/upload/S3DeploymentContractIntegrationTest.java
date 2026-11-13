package com.zeroverse.domain.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.post.repository.PostRepository;
import com.zeroverse.domain.upload.controller.S3UploadController;
import com.zeroverse.domain.upload.controller.UploadController;
import com.zeroverse.domain.upload.dto.UploadDtos.PresignedUploadRequest;
import com.zeroverse.domain.upload.dto.UploadDtos.PresignedUploadResponse;
import com.zeroverse.domain.upload.dto.UploadDtos.UploadResponse;
import com.zeroverse.domain.upload.entity.ImageUpload;
import com.zeroverse.domain.upload.entity.UploadIntent;
import com.zeroverse.domain.upload.repository.ImageUploadRepository;
import com.zeroverse.domain.upload.repository.UploadIntentRepository;
import com.zeroverse.domain.upload.service.S3ObjectStore;
import com.zeroverse.domain.upload.service.S3UploadService;
import com.zeroverse.domain.upload.service.UploadIntentCleanupService;
import com.zeroverse.domain.upload.service.UploadService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.dto.UserSettingsDtos.UpdateProfileRequest;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.domain.user.service.UserSettingsService;
import com.zeroverse.support.MySqlTestSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * X-deployment A-v2의 S3 provider 선택·V7 영속성·현재 ACL 계약을 실제 MySQL에서 검증한다.
 *
 * <p>AWS 네트워크는 이 테스트의 책임이 아니므로 {@link S3ObjectStore}만 대체한다. presign,
 * complete, image binding과 canonical GET, intent 상태 전이는 Spring/JPA/MySQL을 그대로
 * 사용한다. 실제 AWS/browser gate는 별도의 개발 synthetic 검증에서 판정한다.
 */
@SpringBootTest(
        properties = {
            "zeroverse.upload.backend=s3",
            "zeroverse.upload.s3.bucket=zero-verse-s3",
            "zeroverse.upload.s3.region=ap-northeast-2",
            "zeroverse.upload.s3.prefix=zeroverse/dev/test/",
            // The full MySQL suite keeps many cached application contexts alive. Keep this
            // concurrency fixture bounded so its lock assertions cannot starve on idle pools.
            "spring.datasource.hikari.minimum-idle=0",
            "spring.datasource.hikari.maximum-pool-size=4"
        })
@ActiveProfiles("test")
@AutoConfigureMockMvc
class S3DeploymentContractIntegrationTest extends MySqlTestSupport {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-13T00:00:00Z");
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 11, 13, 0, 0);
    private static final String BUCKET = "zero-verse-s3";
    private static final String REGION = "ap-northeast-2";
    private static final String PREFIX = "zeroverse/dev/test/";
    private static final byte[] JPEG = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
        0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0,
        (byte) 0xFF, (byte) 0xD9
    };

    @Autowired private ApplicationContext applicationContext;
    @Autowired private MockMvc mockMvc;
    @Autowired private S3UploadService s3UploadService;
    @Autowired private UploadService uploadService;
    @Autowired private UploadIntentCleanupService cleanupService;
    @Autowired private UserSettingsService userSettingsService;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private ImageUploadRepository imageUploadRepository;
    @Autowired private UploadIntentRepository uploadIntentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private S3ObjectStore objectStore;
    @SpyBean private Clock clock;

    @BeforeEach
    void resetS3Fixtures() {
        // MySqlTestSupport's shared cleaner predates V7 and intentionally preserves migration
        // history. Remove this test's additive table explicitly so repeated classes stay isolated.
        uploadIntentRepository.deleteAllInBatch();
        reset(objectStore);
        doAnswer(invocation -> FIXED_INSTANT).when(clock).instant();
    }

    @AfterEach
    void restoreClockSpy() {
        reset(clock);
    }

    @Test
    void s3BackendExposesS3ControllerOnlyAndDoesNotExposeLocalUploadController() {
        assertThat(applicationContext.getBeansOfType(S3UploadController.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(UploadController.class)).isEmpty();
    }

    @Test
    void presignCompletePersistsV7MetadataAndReadyCompletionIsIdempotent() {
        User owner = user("s3-ready-owner");
        stubS3Object(JPEG, "image/jpeg");

        PresignedUploadResponse presigned = s3UploadService.presign(
                owner.getId(), request("avatar.jpg", UploadPurpose.PROFILE_IMAGE));
        UploadIntent intent = uploadIntentRepository.findById(presigned.id()).orElseThrow();

        assertThat(intent.getStatus()).isEqualTo(UploadIntentStatus.PENDING);
        assertThat(intent.getObjectBucket()).isEqualTo(BUCKET);
        assertThat(intent.getObjectRegion()).isEqualTo(REGION);
        assertThat(intent.getObjectKey()).isEqualTo(PREFIX + presigned.id());
        assertThat(intent.getUploadExpiresAt()).isEqualTo(FIXED_TIME.plusMinutes(5));
        assertThat(intent.getExpiresAt()).isEqualTo(FIXED_TIME.plusHours(24));
        assertThat(presigned.method()).isEqualTo("PUT");
        assertThat(presigned.headers()).containsEntry("If-None-Match", "*");

        UploadResponse first = s3UploadService.complete(owner.getId(), presigned.id());
        UploadResponse second = s3UploadService.complete(owner.getId(), presigned.id());
        ImageUpload image = imageUploadRepository.findById(presigned.id()).orElseThrow();

        assertThat(first).isEqualTo(second);
        assertThat(uploadIntentRepository.findById(presigned.id()).orElseThrow().getStatus())
                .isEqualTo(UploadIntentStatus.READY);
        assertThat(image.getStorageBackend()).isEqualTo(StorageBackend.S3);
        assertThat(image.getStorageKey()).isEqualTo(presigned.id().toString());
        assertThat(image.getImageUrl()).isEqualTo(
                "/api/v1/uploads/" + presigned.id() + "/content");
        assertThat(image.getObjectBucket()).isEqualTo(BUCKET);
        assertThat(image.getObjectRegion()).isEqualTo(REGION);
        assertThat(image.getObjectKey()).isEqualTo(intent.getObjectKey());
        assertThat(image.getContentSha256()).isEqualTo(checksum());
        assertThat(imageUploadRepository.count()).isEqualTo(1);
    }

    @Test
    void existingLocalRowsKeepNullS3LocationAndV7CheckRejectsMixedMetadata() {
        User owner = user("s3-v7-local-owner");
        UUID localId = UUID.randomUUID();
        ImageUpload local = ImageUpload.create(
                localId,
                owner,
                UploadPurpose.POST_IMAGE,
                localId.toString(),
                "/api/v1/uploads/" + localId + "/content",
                "image/jpeg",
                JPEG.length);
        imageUploadRepository.saveAndFlush(local);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT storage_backend, object_bucket, object_region, object_key, content_sha256 "
                        + "FROM image_uploads WHERE id = ?",
                localId.toString());
        assertThat(row).containsEntry("storage_backend", "LOCAL");
        assertThat(row.get("object_bucket")).isNull();
        assertThat(row.get("object_region")).isNull();
        assertThat(row.get("object_key")).isNull();
        assertThat(row.get("content_sha256")).isNull();

        UUID invalidId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO image_uploads "
                        + "(id, owner_user_id, purpose, storage_key, image_url, content_type, file_size, "
                        + "storage_backend, object_bucket) "
                        + "VALUES (?, ?, 'POST_IMAGE', ?, ?, 'image/jpeg', ?, 'LOCAL', 'wrong-bucket')",
                invalidId.toString(),
                owner.getId(),
                invalidId.toString(),
                "/api/v1/uploads/" + invalidId + "/content",
                JPEG.length))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void profileBindingUsesCurrentAclAndCanonicalGetReturnsValidatedBytes() throws Exception {
        User owner = user("s3-profile-owner");
        stubS3Object(JPEG, "image/jpeg");
        UploadResponse uploaded = complete(owner, UploadPurpose.PROFILE_IMAGE);

        userSettingsService.updateProfile(
                owner.getId(),
                new UpdateProfileRequest(
                        owner.getName(),
                        owner.getNickname(),
                        owner.getBio(),
                        owner.getBirthDate(),
                        uploaded.imageUrl()));

        mockMvc.perform(get(uploaded.imageUrl()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(JPEG))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        userSettingsService.updateProfile(
                owner.getId(),
                new UpdateProfileRequest(
                        owner.getName(),
                        owner.getNickname(),
                        owner.getBio(),
                        owner.getBirthDate(),
                        null));

        mockMvc.perform(get(uploaded.imageUrl())).andExpect(status().isNotFound());
    }

    @Test
    void postBindingUsesCurrentPublicAclAndStopsServingDeletedPost() throws Exception {
        User owner = user("s3-post-owner");
        stubS3Object(JPEG, "image/jpeg");
        UploadResponse uploaded = complete(owner, UploadPurpose.POST_THUMBNAIL);
        Blog blog = blogRepository.saveAndFlush(
                Blog.createDefault(owner, "S3 integration blog", "s3-integration-blog"));
        Post post = postRepository.saveAndFlush(Post.create(
                owner,
                blog,
                null,
                "S3 post",
                new ObjectMapper().createObjectNode().put("type", "doc"),
                "<p>S3 post</p>",
                uploaded.imageUrl(),
                Visibility.PUBLIC,
                true,
                FIXED_TIME));
        uploadService.bindPostImages(owner.getId(), post, Set.of(), uploaded.imageUrl());

        mockMvc.perform(get(uploaded.imageUrl()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(JPEG));

        post.softDelete();
        postRepository.saveAndFlush(post);
        mockMvc.perform(get(uploaded.imageUrl())).andExpect(status().isNotFound());
    }

    @Test
    void competingCompletesProduceOneImageAndTwoSuccessfulIdenticalResponses() throws Exception {
        User owner = user("s3-competing-owner");
        stubS3Object(JPEG, "image/jpeg");
        PresignedUploadResponse presigned = s3UploadService.presign(
                owner.getId(), request("race.jpg", UploadPurpose.POST_IMAGE));

        CountDownLatch firstHeadEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstHead = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (first.compareAndSet(true, false)) {
                firstHeadEntered.countDown();
                assertThat(releaseFirstHead.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(objectStore).head(anyString(), anyString(), anyString());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UploadResponse> firstResult = executor.submit(
                    () -> s3UploadService.complete(owner.getId(), presigned.id()));
            assertThat(firstHeadEntered.await(10, TimeUnit.SECONDS)).isTrue();
            Future<UploadResponse> secondResult = executor.submit(
                    () -> s3UploadService.complete(owner.getId(), presigned.id()));
            releaseFirstHead.countDown();

            UploadResponse firstResponse = firstResult.get(30, TimeUnit.SECONDS);
            UploadResponse secondResponse = secondResult.get(30, TimeUnit.SECONDS);
            assertThat(secondResponse).isEqualTo(firstResponse);
            assertThat(imageUploadRepository.count()).isEqualTo(1);
            assertThat(uploadIntentRepository.findById(presigned.id()).orElseThrow().getStatus())
                    .isEqualTo(UploadIntentStatus.READY);
        } finally {
            releaseFirstHead.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void cleanupHonorsDryRunExpiryAndKeepsDeletingTombstoneOnStorageFailure() {
        User owner = user("s3-cleanup-owner");
        UUID id = UUID.randomUUID();
        UploadIntent intent = UploadIntent.pending(
                id,
                owner,
                UploadPurpose.POST_IMAGE,
                JPEG.length,
                "image/jpeg",
                checksum(),
                BUCKET,
                REGION,
                PREFIX + id,
                FIXED_TIME.minusMinutes(1),
                FIXED_TIME.minusHours(1));
        uploadIntentRepository.saveAndFlush(intent);

        assertThat(cleanupService.cleanup(false).candidates()).isEqualTo(1);
        assertThat(uploadIntentRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(UploadIntentStatus.PENDING);

        doThrow(new S3ObjectStore.StorageUnavailableException(
                new IllegalStateException("synthetic storage failure")))
                .when(objectStore).delete(eq(REGION), eq(BUCKET), eq(PREFIX + id));
        UploadIntentCleanupService.CleanupResult result = cleanupService.cleanup(true);

        UploadIntent after = uploadIntentRepository.findById(id).orElseThrow();
        assertThat(result.transitioned()).isEqualTo(1);
        assertThat(result.deleted()).isZero();
        assertThat(result.retried()).isEqualTo(1);
        assertThat(after.getStatus()).isEqualTo(UploadIntentStatus.DELETING);
        assertThat(after.getCleanupAttempts()).isEqualTo(1);
    }

    @Test
    void imageFlushFailureRollsBackIntentAndRetryCanCreateReadyImage() {
        User owner = user("s3-rollback-owner");
        stubS3Object(JPEG, "image/jpeg");
        PresignedUploadResponse presigned = s3UploadService.presign(
                owner.getId(), request("rollback.jpg", UploadPurpose.POST_IMAGE));
        UUID conflictId = UUID.randomUUID();
        ImageUpload conflict = ImageUpload.createS3(
                conflictId,
                owner,
                UploadPurpose.POST_IMAGE,
                conflictId.toString(),
                "/api/v1/uploads/" + conflictId + "/content",
                "image/jpeg",
                JPEG.length,
                BUCKET,
                REGION,
                PREFIX + presigned.id(),
                checksum());
        imageUploadRepository.saveAndFlush(conflict);

        assertThatThrownBy(() -> s3UploadService.complete(owner.getId(), presigned.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(imageUploadRepository.findById(presigned.id())).isEmpty();
        assertThat(uploadIntentRepository.findById(presigned.id()).orElseThrow().getStatus())
                .isEqualTo(UploadIntentStatus.PENDING);

        imageUploadRepository.deleteById(conflictId);
        imageUploadRepository.flush();
        UploadResponse retry = s3UploadService.complete(owner.getId(), presigned.id());

        assertThat(retry.id()).isEqualTo(presigned.id());
        assertThat(uploadIntentRepository.findById(presigned.id()).orElseThrow().getStatus())
                .isEqualTo(UploadIntentStatus.READY);
        assertThat(imageUploadRepository.count()).isEqualTo(1);
    }

    @Test
    void unexpiredCompleteRemainsEligibleWhileCleanupSeesNoCandidate() throws Exception {
        User owner = user("s3-unexpired-race-owner");
        stubS3Object(JPEG, "image/jpeg");
        PresignedUploadResponse presigned = s3UploadService.presign(
                owner.getId(), request("unexpired-race.jpg", UploadPurpose.POST_IMAGE));
        CountDownLatch headEntered = new CountDownLatch(1);
        CountDownLatch releaseHead = new CountDownLatch(1);
        doAnswer(invocation -> {
            headEntered.countDown();
            assertThat(releaseHead.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(objectStore).head(anyString(), anyString(), anyString());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<UploadResponse> complete = executor.submit(
                    () -> s3UploadService.complete(owner.getId(), presigned.id()));
            assertThat(headEntered.await(10, TimeUnit.SECONDS)).isTrue();

            UploadIntentCleanupService.CleanupResult cleanup = cleanupService.cleanup(true);

            assertThat(cleanup.candidates()).isZero();
            assertThat(cleanup.transitioned()).isZero();
            assertThat(cleanup.deleted()).isZero();
            releaseHead.countDown();
            assertThat(complete.get(30, TimeUnit.SECONDS).id()).isEqualTo(presigned.id());
            assertThat(uploadIntentRepository.findById(presigned.id()).orElseThrow().getStatus())
                    .isEqualTo(UploadIntentStatus.READY);
        } finally {
            releaseHead.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void expiredCleanupMarksDeletingBeforeCompleteAndNeverCreatesReadyImage() throws Exception {
        User owner = user("s3-expired-race-owner");
        UUID id = UUID.randomUUID();
        UploadIntent intent = expiredIntent(owner, id);
        uploadIntentRepository.saveAndFlush(intent);
        CountDownLatch deleteEntered = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        doAnswer(invocation -> {
            deleteEntered.countDown();
            assertThat(releaseDelete.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(objectStore).delete(eq(REGION), eq(BUCKET), eq(PREFIX + id));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<UploadIntentCleanupService.CleanupResult> cleanup = executor.submit(
                    () -> cleanupService.cleanup(true));
            assertThat(deleteEntered.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> s3UploadService.complete(owner.getId(), id))
                    .isInstanceOf(BusinessException.class)
                    .extracting(BusinessException.class::cast)
                    .extracting(BusinessException::getErrorCode)
                    .isEqualTo(ErrorCode.UPLOAD_006);
            releaseDelete.countDown();

            UploadIntentCleanupService.CleanupResult result = cleanup.get(30, TimeUnit.SECONDS);
            assertThat(result.deleted()).isEqualTo(1);
            assertThat(uploadIntentRepository.findById(id).orElseThrow().getStatus())
                    .isEqualTo(UploadIntentStatus.DELETED);
            assertThat(imageUploadRepository.findById(id)).isEmpty();
        } finally {
            releaseDelete.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void storageTimeoutRollsBackAndReleasesOwnerLockForTheNextTransaction() throws Exception {
        User owner = user("s3-timeout-owner");
        stubS3Object(JPEG, "image/jpeg");
        PresignedUploadResponse presigned = s3UploadService.presign(
                owner.getId(), request("timeout.jpg", UploadPurpose.POST_IMAGE));
        CountDownLatch headEntered = new CountDownLatch(1);
        CountDownLatch releaseHead = new CountDownLatch(1);
        CountDownLatch lockAttemptStarted = new CountDownLatch(1);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        doAnswer(invocation -> {
            headEntered.countDown();
            assertThat(releaseHead.await(10, TimeUnit.SECONDS)).isTrue();
            throw new S3ObjectStore.StorageUnavailableException(
                    new IllegalStateException("synthetic S3 timeout"));
        }).when(objectStore).head(anyString(), anyString(), anyString());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ErrorCode> failedComplete = executor.submit(() -> {
                try {
                    s3UploadService.complete(owner.getId(), presigned.id());
                    return null;
                } catch (BusinessException exception) {
                    return exception.getErrorCode();
                }
            });
            assertThat(headEntered.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Long> nextTransaction = executor.submit(() -> {
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                return transaction.execute(status -> {
                    lockAttemptStarted.countDown();
                    User locked = userRepository.findByIdAndDeletedAtIsNullForUpdate(owner.getId())
                            .orElseThrow();
                    lockAcquired.countDown();
                    return locked.getId();
                });
            });
            assertThat(lockAttemptStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(lockAcquired.await(250, TimeUnit.MILLISECONDS)).isFalse();

            releaseHead.countDown();
            assertThat(failedComplete.get(30, TimeUnit.SECONDS)).isEqualTo(ErrorCode.UPLOAD_008);
            assertThat(lockAcquired.await(10, TimeUnit.SECONDS)).isTrue();
            Long lockedUserId = nextTransaction.get(30, TimeUnit.SECONDS);

            assertThat(lockedUserId).isEqualTo(owner.getId());
            assertThat(uploadIntentRepository.findById(presigned.id()).orElseThrow().getStatus())
                    .isEqualTo(UploadIntentStatus.PENDING);
        } finally {
            releaseHead.countDown();
            executor.shutdownNow();
        }
    }

    private UploadResponse complete(User owner, UploadPurpose purpose) {
        PresignedUploadResponse presigned = s3UploadService.presign(
                owner.getId(), request("image.jpg", purpose));
        return s3UploadService.complete(owner.getId(), presigned.id());
    }

    private PresignedUploadRequest request(String fileName, UploadPurpose purpose) {
        return new PresignedUploadRequest(
                fileName, "image/jpeg", (long) JPEG.length, purpose, checksum());
    }

    private void stubS3Object(byte[] bytes, String contentType) {
        when(objectStore.presignPut(anyString(), anyString(), anyString(), anyLong(), anyString()))
                .thenAnswer(invocation -> new S3ObjectStore.PresignedPut(
                        "https://signed.example/" + UUID.randomUUID(),
                        Map.of(
                                "Content-Type", contentType,
                                "x-amz-checksum-sha256", checksum(),
                                "If-None-Match", "*")));
        doNothing().when(objectStore).head(anyString(), anyString(), anyString());
        when(objectStore.get(anyString(), anyString(), anyString()))
                .thenReturn(new S3ObjectStore.ObjectSnapshot(bytes, contentType));
    }

    private UploadIntent expiredIntent(User owner, UUID id) {
        return UploadIntent.pending(
                id,
                owner,
                UploadPurpose.POST_IMAGE,
                JPEG.length,
                "image/jpeg",
                checksum(),
                BUCKET,
                REGION,
                PREFIX + id,
                FIXED_TIME.minusMinutes(1),
                FIXED_TIME.minusHours(1));
    }

    private User user(String suffix) {
        return userRepository.saveAndFlush(User.register(
                suffix + "@zeroverse.test",
                "password-hash",
                "S3 Test User",
                suffix,
                LocalDate.of(1990, 1, 1)));
    }

    private static String checksum() {
        return com.zeroverse.domain.upload.service.UploadChecksum.sha256Base64(JPEG);
    }
}
