package com.zeroverse.domain.upload.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.PostImage;
import com.zeroverse.domain.post.repository.PostImageRepository;
import com.zeroverse.domain.post.service.PostAccessPolicy;
import com.zeroverse.domain.upload.StorageBackend;
import com.zeroverse.domain.upload.UploadIntentStatus;
import com.zeroverse.domain.upload.UploadPurpose;
import com.zeroverse.domain.upload.config.S3StorageProperties;
import com.zeroverse.domain.upload.config.UploadProperties;
import com.zeroverse.domain.upload.dto.UploadDtos.ImageContent;
import com.zeroverse.domain.upload.dto.UploadDtos.PresignedUploadRequest;
import com.zeroverse.domain.upload.dto.UploadDtos.PresignedUploadResponse;
import com.zeroverse.domain.upload.dto.UploadDtos.UploadResponse;
import com.zeroverse.domain.upload.entity.ImageUpload;
import com.zeroverse.domain.upload.entity.UploadIntent;
import com.zeroverse.domain.upload.repository.ImageUploadRepository;
import com.zeroverse.domain.upload.repository.UploadIntentRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.tika.Tika;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** S3 intent, complete, and canonical binary read boundary. */
@Service
@ConditionalOnProperty(prefix = "zeroverse.upload", name = "backend", havingValue = "s3")
public class S3UploadService {

    private static final String URL_PREFIX = "/api/v1/uploads/";
    private static final String URL_SUFFIX = "/content";
    private static final Set<String> SUPPORTED_TYPES = LocalImageStore.SUPPORTED_TYPES;
    private static final Duration INTENT_TTL = Duration.ofHours(24);

    private final ImageUploadRepository imageUploadRepository;
    private final UploadIntentRepository uploadIntentRepository;
    private final UserRepository userRepository;
    private final S3ObjectStore objectStore;
    private final S3StorageProperties s3Properties;
    private final UploadProperties uploadProperties;
    private final PostAccessPolicy postAccessPolicy;
    private final PostImageRepository postImageRepository;
    private final EntityManager entityManager;
    private final Clock clock;
    private final Tika tika;

    public S3UploadService(
            ImageUploadRepository imageUploadRepository,
            UploadIntentRepository uploadIntentRepository,
            UserRepository userRepository,
            S3ObjectStore objectStore,
            S3StorageProperties s3Properties,
            UploadProperties uploadProperties,
            PostAccessPolicy postAccessPolicy,
            PostImageRepository postImageRepository,
            EntityManager entityManager,
            Clock clock) {
        this.imageUploadRepository = imageUploadRepository;
        this.uploadIntentRepository = uploadIntentRepository;
        this.userRepository = userRepository;
        this.objectStore = objectStore;
        this.s3Properties = s3Properties;
        this.uploadProperties = uploadProperties;
        this.postAccessPolicy = postAccessPolicy;
        this.postImageRepository = postImageRepository;
        this.entityManager = entityManager;
        this.clock = clock;
        this.tika = new Tika();
    }

    @Transactional(timeout = 30)
    public PresignedUploadResponse presign(Long userId, PresignedUploadRequest request) {
        User owner = activeUser(userId);
        if (request == null || request.purpose() == null) {
            throw new BusinessException(ErrorCode.UPLOAD_004);
        }
        long size = request.size() == null ? 0 : request.size();
        if (size < 1 || size > S3ObjectStore.MAX_BYTES) {
            throw new BusinessException(ErrorCode.UPLOAD_002);
        }
        String contentType = normalizeType(request.contentType());
        if (!SUPPORTED_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.UPLOAD_001);
        }
        if (request.fileName() == null || request.fileName().isBlank()
                || request.fileName().length() > 255) {
            throw new BusinessException(ErrorCode.VALIDATION_001);
        }
        String checksum = UploadChecksum.canonicalize(request.checksumSha256());
        requireS3Location();

        UUID id = UUID.randomUUID();
        String objectKey = s3Properties.normalizedPrefix() + id;
        LocalDateTime now = now();
        UploadIntent intent = UploadIntent.pending(
                id,
                owner,
                request.purpose(),
                size,
                contentType,
                checksum,
                s3Properties.getBucket(),
                s3Properties.getRegion(),
                objectKey,
                now.plus(S3ObjectStore.UPLOAD_TTL),
                now.plus(INTENT_TTL));
        uploadIntentRepository.saveAndFlush(intent);
        S3ObjectStore.PresignedPut put = objectStore.presignPut(
                intent.getObjectBucket(),
                intent.getObjectKey(),
                intent.getExpectedContentType(),
                intent.getExpectedSize(),
                intent.getExpectedSha256());
        return new PresignedUploadResponse(
                id,
                put.url(),
                "PUT",
                put.headers(),
                intent.getUploadExpiresAt().toInstant(ZoneOffset.UTC));
    }

    @Transactional(timeout = 30)
    public UploadResponse complete(Long userId, UUID intentId) {
        User owner = activeUserForUpdate(userId);
        UploadIntent intent = uploadIntentRepository.findByIdForUpdate(intentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_003));
        if (intent.getOwner() == null || !userId.equals(intent.getOwner().getId())) {
            throw new BusinessException(ErrorCode.UPLOAD_003);
        }
        if (!owner.getId().equals(intent.getOwner().getId())) {
            throw new BusinessException(ErrorCode.UPLOAD_003);
        }
        if (intent.getStatus() == UploadIntentStatus.READY) {
            ImageUpload image = imageUploadRepository.findById(intent.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_500));
            return toResponse(image);
        }
        if (intent.getStatus() != UploadIntentStatus.PENDING
                || !now().isBefore(intent.getExpiresAt())) {
            throw new BusinessException(ErrorCode.UPLOAD_006);
        }

        try {
            objectStore.head(intent.getObjectRegion(), intent.getObjectBucket(), intent.getObjectKey());
        } catch (S3ObjectStore.ObjectMissingException e) {
            if (e.definitelyMissing()) {
                throw new BusinessException(ErrorCode.UPLOAD_005);
            }
            throw new BusinessException(ErrorCode.UPLOAD_008);
        } catch (S3ObjectStore.StorageUnavailableException e) {
            throw new BusinessException(ErrorCode.UPLOAD_008);
        }

        S3ObjectStore.ObjectSnapshot snapshot;
        try {
            snapshot = objectStore.get(intent.getObjectRegion(), intent.getObjectBucket(), intent.getObjectKey());
        } catch (S3ObjectStore.ObjectMissingException e) {
            if (e.definitelyMissing()) {
                throw new BusinessException(ErrorCode.UPLOAD_005);
            }
            throw new BusinessException(ErrorCode.UPLOAD_008);
        } catch (S3ObjectStore.ObjectTooLargeException e) {
            throw new BusinessException(ErrorCode.UPLOAD_002);
        } catch (S3ObjectStore.StorageUnavailableException e) {
            throw new BusinessException(ErrorCode.UPLOAD_008);
        }
        byte[] bytes = snapshot.bytes();
        if (bytes.length != intent.getExpectedSize()) {
            throw new BusinessException(ErrorCode.UPLOAD_002);
        }
        String detectedType = normalizeType(tika.detect(bytes));
        String storedType = normalizeType(snapshot.contentType());
        if (!SUPPORTED_TYPES.contains(detectedType)
                || !intent.getExpectedContentType().equals(detectedType)
                || !intent.getExpectedContentType().equals(storedType)) {
            throw new BusinessException(ErrorCode.UPLOAD_001);
        }
        if (!intent.getExpectedSha256().equals(UploadChecksum.sha256Base64(bytes))) {
            throw new BusinessException(ErrorCode.UPLOAD_007);
        }

        ImageUpload image = ImageUpload.createS3(
                intent.getId(),
                owner,
                intent.getPurpose(),
                intent.getId().toString(),
                uploadProperties.contentUrl(intent.getId()),
                intent.getExpectedContentType(),
                bytes.length,
                intent.getObjectBucket(),
                intent.getObjectRegion(),
                intent.getObjectKey(),
                intent.getExpectedSha256());
        imageUploadRepository.saveAndFlush(image);
        intent.markReady();
        uploadIntentRepository.saveAndFlush(intent);
        return toResponse(image);
    }

    @Transactional(readOnly = true)
    public ImageContent read(UUID id, Long viewerId) {
        ImageUpload image = imageUploadRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_003));
        if (image.getStorageBackend() != StorageBackend.S3
                || !canonicalUrl(id).equals(image.getImageUrl())
                || !id.toString().equals(image.getStorageKey())
                || image.getObjectBucket() == null
                || image.getObjectRegion() == null
                || image.getObjectKey() == null
                || image.getContentSha256() == null) {
            throw new BusinessException(ErrorCode.UPLOAD_003);
        }
        User owner = image.getOwner();
        if (owner == null || owner.isDeleted()) {
            throw new BusinessException(ErrorCode.UPLOAD_003);
        }
        if (viewerId != null && userRepository.findByIdAndDeletedAtIsNull(viewerId).isEmpty()) {
            throw new BusinessException(ErrorCode.UPLOAD_003);
        }
        boolean ownerViewer = viewerId != null && viewerId.equals(owner.getId());
        boolean readable = switch (image.getPurpose()) {
            case PROFILE_IMAGE -> canReadProfile(image, owner, ownerViewer);
            case POST_THUMBNAIL, POST_IMAGE -> canReadPost(image, viewerId, ownerViewer);
        };
        if (!readable) {
            throw new BusinessException(ErrorCode.UPLOAD_003);
        }

        S3ObjectStore.ObjectSnapshot snapshot;
        try {
            snapshot = objectStore.get(image.getObjectRegion(), image.getObjectBucket(), image.getObjectKey());
        } catch (S3ObjectStore.ObjectMissingException e) {
            throw new BusinessException(ErrorCode.UPLOAD_003);
        } catch (S3ObjectStore.ObjectTooLargeException e) {
            throw new BusinessException(ErrorCode.UPLOAD_003);
        } catch (S3ObjectStore.StorageUnavailableException e) {
            throw new BusinessException(ErrorCode.UPLOAD_008);
        }
        byte[] bytes = snapshot.bytes();
        String detectedType = normalizeType(tika.detect(bytes));
        if (bytes.length != image.getFileSize()
                || !SUPPORTED_TYPES.contains(detectedType)
                || !detectedType.equals(normalizeType(image.getContentType()))
                || !image.getContentSha256().equals(UploadChecksum.sha256Base64(bytes))) {
            throw new BusinessException(ErrorCode.UPLOAD_003);
        }
        return new ImageContent(bytes, image.getContentType(), bytes.length);
    }

    private boolean canReadProfile(ImageUpload image, User owner, boolean ownerViewer) {
        boolean current = image.isCurrentlyBound()
                && image.getBoundPost() == null
                && ImageUpload.RESOURCE_PROFILE.equals(image.getBoundResourceType())
                && String.valueOf(owner.getId()).equals(image.getBoundResourceId())
                && image.getImageUrl().equals(owner.getProfileImageUrl());
        return current || ownerViewer;
    }

    private boolean canReadPost(ImageUpload image, Long viewerId, boolean ownerViewer) {
        Post post = image.getBoundPost();
        if (post == null) {
            return ownerViewer;
        }
        if (post.isDeleted() || post.getBlog() == null || post.getBlog().isDeleted()
                || post.getUser() == null || post.getUser().isDeleted()) {
            return false;
        }
        boolean current = image.isCurrentlyBound() && currentPostReference(image, post);
        if (!current) {
            return ownerViewer;
        }
        return postAccessPolicy.canRead(post, viewerId);
    }

    private boolean currentPostReference(ImageUpload image, Post post) {
        if (ImageUpload.RESOURCE_THUMBNAIL.equals(image.getBoundResourceType())) {
            return image.getImageUrl().equals(post.getThumbnailUrl());
        }
        if (ImageUpload.RESOURCE_BODY.equals(image.getBoundResourceType())) {
            Long postId = post.getId();
            return postId != null && entityManager.createQuery(
                            "select count(pi) from PostImage pi where pi.post.id = :postId "
                                    + "and pi.imageUrl = :imageUrl and pi.deletedAt is null", Long.class)
                    .setParameter("postId", postId)
                    .setParameter("imageUrl", image.getImageUrl())
                    .getSingleResult() > 0;
        }
        return false;
    }

    private UploadResponse toResponse(ImageUpload image) {
        return new UploadResponse(
                image.getId(),
                image.getImageUrl(),
                image.getContentType(),
                image.getFileSize(),
                image.getPurpose());
    }

    private User activeUser(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_003);
        }
        return user;
    }

    private User activeUserForUpdate(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNullForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_003);
        }
        return user;
    }

    private void requireS3Location() {
        if (s3Properties.getBucket() == null || s3Properties.getBucket().isBlank()
                || s3Properties.getRegion() == null || s3Properties.getRegion().isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String canonicalUrl(UUID id) {
        return URL_PREFIX + id + URL_SUFFIX;
    }

    private static String normalizeType(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
