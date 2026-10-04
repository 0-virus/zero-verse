package com.zeroverse.domain.upload.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.PostImage;
import com.zeroverse.domain.post.repository.PostImageRepository;
import com.zeroverse.domain.post.service.PostAccessPolicy;
import com.zeroverse.domain.upload.UploadPurpose;
import com.zeroverse.domain.upload.config.UploadProperties;
import com.zeroverse.domain.upload.dto.UploadDtos.ImageContent;
import com.zeroverse.domain.upload.dto.UploadDtos.UploadResponse;
import com.zeroverse.domain.upload.entity.ImageUpload;
import com.zeroverse.domain.upload.repository.ImageUploadRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** 로컬 이미지 upload/read와 최초 binding·현재 연결 권한을 조정한다. */
@Service
public class UploadService {

    private static final String URL_PREFIX = "/api/v1/uploads/";
    private static final String URL_SUFFIX = "/content";

    private final ImageUploadRepository imageUploadRepository;
    private final UserRepository userRepository;
    private final LocalImageStore localImageStore;
    private final UploadProperties uploadProperties;
    private final PostAccessPolicy postAccessPolicy;
    private final PostImageRepository postImageRepository;
    private final EntityManager entityManager;

    public UploadService(
            ImageUploadRepository imageUploadRepository,
            UserRepository userRepository,
            LocalImageStore localImageStore,
            UploadProperties uploadProperties,
            PostAccessPolicy postAccessPolicy,
            PostImageRepository postImageRepository,
            EntityManager entityManager) {
        this.imageUploadRepository = imageUploadRepository;
        this.userRepository = userRepository;
        this.localImageStore = localImageStore;
        this.uploadProperties = uploadProperties;
        this.postAccessPolicy = postAccessPolicy;
        this.postImageRepository = postImageRepository;
        this.entityManager = entityManager;
    }

    /** 파일을 먼저 저장하고 metadata flush까지 성공한 경우에만 response를 만든다. */
    @Transactional
    public UploadResponse upload(Long userId, MultipartFile file, UploadPurpose purpose) {
        User owner = activeUser(userId);
        if (purpose == null) {
            throw new BusinessException(ErrorCode.UPLOAD_004);
        }

        UUID id = UUID.randomUUID();
        LocalImageStore.StoredImage stored = localImageStore.store(id, file);
        ImageUpload metadata = ImageUpload.create(
                id,
                owner,
                purpose,
                stored.storageKey(),
                uploadProperties.contentUrl(id),
                stored.contentType(),
                stored.size());

        try {
            imageUploadRepository.saveAndFlush(metadata);
            registerRollbackCleanup(stored.storageKey());
            return new UploadResponse(
                    id, metadata.getImageUrl(), metadata.getContentType(), metadata.getFileSize(), purpose);
        } catch (RuntimeException e) {
            localImageStore.delete(stored.storageKey());
            throw e;
        }
    }

    /** 현재 DB 연결과 접근 predicate를 다시 평가한 뒤 binary를 반환한다. */
    @Transactional(readOnly = true)
    public ImageContent read(UUID id, Long viewerId) {
        ImageUpload image = imageUploadRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_003));
        if (!uploadProperties.contentUrl(id).equals(image.getImageUrl())
                || !id.toString().equals(image.getStorageKey())) {
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

        byte[] bytes = localImageStore.readValidated(
                image.getStorageKey(), image.getContentType(), image.getFileSize());
        return new ImageContent(bytes, image.getContentType(), bytes.length);
    }

    /** Post snapshot의 본문·대표 URL을 최초 binding metadata와 원자적으로 맞춘다. */
    @Transactional
    public void bindPostImages(Long userId, Post post, Set<String> bodyUrls, String thumbnailUrl) {
        User owner = activeUserForUpdate(userId);
        if (post == null
                || post.getId() == null
                || post.isDeleted()
                || post.getBlog() == null
                || post.getBlog().isDeleted()
                || post.getUser() == null
                || post.getUser().isDeleted()
                || !userId.equals(post.getUser().getId())) {
            throw new BusinessException(ErrorCode.UPLOAD_004);
        }

        Map<String, String> expected = new LinkedHashMap<>();
        if (bodyUrls != null) {
            for (String bodyUrl : bodyUrls) {
                requireCanonicalUrl(bodyUrl);
                if (thumbnailUrl != null && thumbnailUrl.equals(bodyUrl)) {
                    throw new BusinessException(ErrorCode.UPLOAD_004);
                }
                if (expected.put(bodyUrl, ImageUpload.RESOURCE_BODY) != null) {
                    throw new BusinessException(ErrorCode.UPLOAD_004);
                }
            }
        }
        if (thumbnailUrl != null) {
            requireCanonicalUrl(thumbnailUrl);
            if (expected.put(thumbnailUrl, ImageUpload.RESOURCE_THUMBNAIL) != null) {
                throw new BusinessException(ErrorCode.UPLOAD_004);
            }
        }

        Set<String> activeBodyUrls = postImageRepository
                .findByPostIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(post.getId())
                .stream()
                .map(PostImage::getImageUrl)
                .collect(Collectors.toSet());
        Set<String> requestedBodyUrls = bodyUrls == null ? Set.of() : Set.copyOf(bodyUrls);
        if (!activeBodyUrls.equals(requestedBodyUrls)) {
            throw new BusinessException(ErrorCode.UPLOAD_004);
        }

        List<ImageUpload> current = imageUploadRepository.findCurrentlyBoundByPostId(post.getId());
        for (ImageUpload image : current) {
            if (!expected.containsKey(image.getImageUrl())) {
                image.detach(LocalDateTime.now());
                imageUploadRepository.save(image);
            }
        }

        List<Map.Entry<String, String>> expectedEntries = new ArrayList<>(expected.entrySet());
        expectedEntries.sort(Comparator.comparing(entry -> parseCanonicalUrl(entry.getKey())));
        for (Map.Entry<String, String> entry : expectedEntries) {
            ImageUpload image = imageUploadRepository.findByImageUrlForUpdate(entry.getKey())
                    .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_004));
            UUID expectedId = parseCanonicalUrl(entry.getKey());
            if (image.getId() == null
                    || !expectedId.equals(image.getId())
                    || !expectedId.toString().equals(image.getStorageKey())
                    || image.getOwner() == null
                    || !userId.equals(image.getOwner().getId())
                    || image.getPurpose() != purposeFor(entry.getValue())) {
                throw new BusinessException(ErrorCode.UPLOAD_004);
            }

            if (!image.isBound()) {
                image.bindToPost(post, entry.getValue(), LocalDateTime.now());
                imageUploadRepository.save(image);
                continue;
            }

            boolean sameCurrentBinding = image.isCurrentlyBound()
                    && image.getBoundPost() != null
                    && post.getId().equals(image.getBoundPost().getId())
                    && entry.getValue().equals(image.getBoundResourceType());
            boolean sameOriginalBinding = image.getBoundPost() != null
                    && post.getId().equals(image.getBoundPost().getId())
                    && entry.getValue().equals(image.getBoundResourceType());
            if (sameCurrentBinding) {
                continue;
            }
            if (sameOriginalBinding && image.isDetached()) {
                image.reattachToOriginalPost();
                imageUploadRepository.save(image);
                continue;
            }
            if (!sameOriginalBinding) {
                throw new BusinessException(ErrorCode.UPLOAD_004);
            }
        }
        // Force the metadata mutation into the same transaction as the post snapshot.
        imageUploadRepository.flush();
        // Ensure the active owner was actually loaded even when a caller supplied a detached Post.
        if (!owner.getId().equals(post.getUser().getId())) {
            throw new BusinessException(ErrorCode.UPLOAD_004);
        }
    }

    /** 신규 관리 URL을 사용자의 현재 profile binding으로 만든다. 기존 외부 URL은 읽기만 호환한다. */
    @Transactional
    public void bindProfileImage(Long userId, String imageUrl) {
        User owner = activeUserForUpdate(userId);
        if (imageUrl == null || imageUrl.isBlank()) {
            detachCurrentProfileImage(owner);
            return;
        }
        if (!isManagedImageUrl(imageUrl)) {
            throw new BusinessException(ErrorCode.UPLOAD_004);
        }

        UUID id = parseCanonicalUrl(imageUrl);
        ImageUpload image = imageUploadRepository.findByImageUrlForUpdate(imageUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_004));
        if (image.getId() == null
                || !image.getId().equals(id)
                || !id.toString().equals(image.getStorageKey())
                || image.getOwner() == null
                || !userId.equals(image.getOwner().getId())
                || image.getPurpose() != UploadPurpose.PROFILE_IMAGE) {
            throw new BusinessException(ErrorCode.UPLOAD_004);
        }

        boolean sameCurrentBinding = image.isCurrentlyBound()
                && image.getBoundPost() == null
                && ImageUpload.RESOURCE_PROFILE.equals(image.getBoundResourceType())
                && String.valueOf(userId).equals(image.getBoundResourceId())
                && imageUrl.equals(owner.getProfileImageUrl());
        if (image.isBound() && !sameCurrentBinding) {
            throw new BusinessException(ErrorCode.UPLOAD_004);
        }

        String previousUrl = owner.getProfileImageUrl();
        if (!sameCurrentBinding) {
            if (previousUrl != null && !previousUrl.equals(imageUrl) && isManagedImageUrl(previousUrl)) {
                imageUploadRepository.findByImageUrlForUpdate(previousUrl)
                        .filter(previous -> previous.getOwner() != null
                                && userId.equals(previous.getOwner().getId())
                                && previous.getPurpose() == UploadPurpose.PROFILE_IMAGE
                                && previous.isCurrentlyBound()
                                && previous.getBoundPost() == null
                                && ImageUpload.RESOURCE_PROFILE.equals(previous.getBoundResourceType())
                                && String.valueOf(userId).equals(previous.getBoundResourceId()))
                        .ifPresent(previous -> {
                            previous.detach(LocalDateTime.now());
                            imageUploadRepository.save(previous);
                        });
            }
            image.bindToProfile(userId, LocalDateTime.now());
            imageUploadRepository.save(image);
        }
        imageUploadRepository.flush();
        if (!owner.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.UPLOAD_004);
        }
    }

    public boolean isManagedImageUrl(String imageUrl) {
        return imageUrl != null && imageUrl.startsWith(URL_PREFIX) && imageUrl.endsWith(URL_SUFFIX);
    }

    public UUID parseCanonicalUrl(String imageUrl) {
        if (!isManagedImageUrl(imageUrl)) {
            throw new BusinessException(ErrorCode.UPLOAD_004);
        }
        String value = imageUrl.substring(URL_PREFIX.length(), imageUrl.length() - URL_SUFFIX.length());
        try {
            UUID id = UUID.fromString(value);
            if (!uploadProperties.contentUrl(id).equals(imageUrl)) {
                throw new IllegalArgumentException("non-canonical UUID");
            }
            return id;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UPLOAD_004);
        }
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

    private void detachCurrentProfileImage(User owner) {
        String previousUrl = owner.getProfileImageUrl();
        if (previousUrl == null || previousUrl.isBlank() || !isManagedImageUrl(previousUrl)) {
            return;
        }
        imageUploadRepository.findByImageUrlForUpdate(previousUrl)
                .filter(previous -> previous.getOwner() != null
                        && owner.getId().equals(previous.getOwner().getId())
                        && previous.getPurpose() == UploadPurpose.PROFILE_IMAGE
                        && previous.isCurrentlyBound()
                        && previous.getBoundPost() == null
                        && ImageUpload.RESOURCE_PROFILE.equals(previous.getBoundResourceType()))
                .ifPresent(previous -> {
                    previous.detach(LocalDateTime.now());
                    imageUploadRepository.save(previous);
                });
        imageUploadRepository.flush();
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

    private static UploadPurpose purposeFor(String resourceType) {
        return switch (resourceType) {
            case ImageUpload.RESOURCE_BODY -> UploadPurpose.POST_IMAGE;
            case ImageUpload.RESOURCE_THUMBNAIL -> UploadPurpose.POST_THUMBNAIL;
            default -> null;
        };
    }

    private void requireCanonicalUrl(String imageUrl) {
        parseCanonicalUrl(imageUrl);
    }

    private void registerRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    localImageStore.delete(storageKey);
                }
            }
        });
    }
}
