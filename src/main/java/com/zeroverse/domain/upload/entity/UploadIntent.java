package com.zeroverse.domain.upload.entity;

import com.zeroverse.common.entity.BaseEntity;
import com.zeroverse.domain.upload.UploadIntentStatus;
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

/** Durable state for a direct-to-S3 upload before an ImageUpload row exists. */
@Entity
@Table(name = "upload_intents")
public class UploadIntent extends BaseEntity {

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

    @Column(name = "expected_size", nullable = false)
    private Long expectedSize;

    @Column(name = "expected_content_type", nullable = false, length = 100)
    private String expectedContentType;

    @Column(name = "expected_sha256", nullable = false, length = 44, columnDefinition = "CHAR(44)")
    private String expectedSha256;

    @Column(name = "object_bucket", nullable = false, length = 255)
    private String objectBucket;

    @Column(name = "object_region", nullable = false, length = 64)
    private String objectRegion;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private UploadIntentStatus status;

    @Column(name = "upload_expires_at", nullable = false)
    private LocalDateTime uploadExpiresAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "cleanup_attempts", nullable = false)
    private Integer cleanupAttempts;

    @Column(name = "last_cleanup_at")
    private LocalDateTime lastCleanupAt;

    protected UploadIntent() {}

    private UploadIntent(
            UUID id,
            User owner,
            UploadPurpose purpose,
            long expectedSize,
            String expectedContentType,
            String expectedSha256,
            String objectBucket,
            String objectRegion,
            String objectKey,
            UploadIntentStatus status,
            LocalDateTime uploadExpiresAt,
            LocalDateTime expiresAt) {
        this.id = id;
        this.owner = owner;
        this.purpose = purpose;
        this.expectedSize = expectedSize;
        this.expectedContentType = expectedContentType;
        this.expectedSha256 = expectedSha256;
        this.objectBucket = objectBucket;
        this.objectRegion = objectRegion;
        this.objectKey = objectKey;
        this.status = status;
        this.uploadExpiresAt = uploadExpiresAt;
        this.expiresAt = expiresAt;
        this.cleanupAttempts = 0;
    }

    public static UploadIntent pending(
            UUID id,
            User owner,
            UploadPurpose purpose,
            long expectedSize,
            String expectedContentType,
            String expectedSha256,
            String objectBucket,
            String objectRegion,
            String objectKey,
            LocalDateTime uploadExpiresAt,
            LocalDateTime expiresAt) {
        return new UploadIntent(
                id,
                owner,
                purpose,
                expectedSize,
                expectedContentType,
                expectedSha256,
                objectBucket,
                objectRegion,
                objectKey,
                UploadIntentStatus.PENDING,
                uploadExpiresAt,
                expiresAt);
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

    public Long getExpectedSize() {
        return expectedSize;
    }

    public String getExpectedContentType() {
        return expectedContentType;
    }

    public String getExpectedSha256() {
        return expectedSha256;
    }

    public String getObjectBucket() {
        return objectBucket;
    }

    public String getObjectRegion() {
        return objectRegion;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public UploadIntentStatus getStatus() {
        return status;
    }

    public LocalDateTime getUploadExpiresAt() {
        return uploadExpiresAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public Integer getCleanupAttempts() {
        return cleanupAttempts;
    }

    public LocalDateTime getLastCleanupAt() {
        return lastCleanupAt;
    }

    public boolean isPending() {
        return status == UploadIntentStatus.PENDING;
    }

    public boolean isTerminal() {
        return status == UploadIntentStatus.DELETED;
    }

    public void markReady() {
        requireStatus(UploadIntentStatus.PENDING);
        status = UploadIntentStatus.READY;
    }

    public void markDeleting(LocalDateTime now) {
        requireStatus(UploadIntentStatus.PENDING, UploadIntentStatus.DELETING);
        status = UploadIntentStatus.DELETING;
        lastCleanupAt = now;
    }

    public void markDeleted(LocalDateTime now) {
        requireStatus(UploadIntentStatus.DELETING);
        status = UploadIntentStatus.DELETED;
        lastCleanupAt = now;
    }

    public void recordCleanupFailure(LocalDateTime now) {
        if (cleanupAttempts == null) {
            cleanupAttempts = 0;
        }
        cleanupAttempts++;
        lastCleanupAt = now;
    }

    private void requireStatus(UploadIntentStatus... allowed) {
        for (UploadIntentStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("invalid upload intent state transition");
    }
}
