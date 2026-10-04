package com.zeroverse.domain.upload.entity;

import com.zeroverse.common.entity.BaseEntity;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.upload.UploadPurpose;
import com.zeroverse.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 로컬 업로드 파일과 영구 최초 binding metadata. 실제 바이트는 DB에 저장하지 않는다. */
@Entity
@Table(name = "image_uploads")
public class ImageUpload extends BaseEntity {

    public static final String RESOURCE_PROFILE = "PROFILE_IMAGE";
    public static final String RESOURCE_THUMBNAIL = "POST_THUMBNAIL";
    public static final String RESOURCE_BODY = "POST_IMAGE";

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private UploadPurpose purpose;

    @Column(name = "storage_key", nullable = false, unique = true, length = 500)
    private String storageKey;

    @Column(name = "image_url", nullable = false, unique = true, length = 500)
    private String imageUrl;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bound_post_id")
    private Post boundPost;

    @Column(name = "bound_resource_type", length = 30)
    private String boundResourceType;

    @Column(name = "bound_resource_id", length = 100)
    private String boundResourceId;

    @Column(name = "bound_at")
    private LocalDateTime boundAt;

    @Column(name = "detached_at")
    private LocalDateTime detachedAt;

    protected ImageUpload() {}

    private ImageUpload(
            UUID id,
            User owner,
            UploadPurpose purpose,
            String storageKey,
            String imageUrl,
            String contentType,
            long fileSize) {
        this.id = id;
        this.owner = owner;
        this.purpose = purpose;
        this.storageKey = storageKey;
        this.imageUrl = imageUrl;
        this.contentType = contentType;
        this.fileSize = fileSize;
    }

    public static ImageUpload create(
            UUID id,
            User owner,
            UploadPurpose purpose,
            String storageKey,
            String imageUrl,
            String contentType,
            long fileSize) {
        return new ImageUpload(id, owner, purpose, storageKey, imageUrl, contentType, fileSize);
    }

    public UUID getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public UploadPurpose getPurpose() {
        return purpose;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public Post getBoundPost() {
        return boundPost;
    }

    public String getBoundResourceType() {
        return boundResourceType;
    }

    public String getBoundResourceId() {
        return boundResourceId;
    }

    public LocalDateTime getBoundAt() {
        return boundAt;
    }

    public LocalDateTime getDetachedAt() {
        return detachedAt;
    }

    public boolean isBound() {
        return boundAt != null;
    }

    public boolean isDetached() {
        return detachedAt != null;
    }

    public boolean isCurrentlyBound() {
        return isBound() && !isDetached();
    }

    public void bindToPost(Post post, String resourceType, LocalDateTime now) {
        this.boundPost = post;
        this.boundResourceType = resourceType;
        this.boundResourceId = post.getId() == null ? null : post.getId().toString();
        this.boundAt = now;
        this.detachedAt = null;
    }

    /** 같은 최초 post binding으로 되돌릴 때 최초 연결 시각과 리소스 소유권은 보존한다. */
    public void reattachToOriginalPost() {
        if (this.boundPost == null || this.boundAt == null || this.detachedAt == null) {
            throw new IllegalStateException("original post binding is not detached");
        }
        this.detachedAt = null;
    }

    public void bindToProfile(Long userId, LocalDateTime now) {
        this.boundPost = null;
        this.boundResourceType = RESOURCE_PROFILE;
        this.boundResourceId = userId == null ? null : userId.toString();
        this.boundAt = now;
        this.detachedAt = null;
    }

    public void detach(LocalDateTime now) {
        if (this.boundAt != null && this.detachedAt == null) {
            this.detachedAt = now;
        }
    }
}
