package com.zeroverse.domain.post.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "post_images", uniqueConstraints = {
    @UniqueConstraint(name = "uk_post_display_order", columnNames = {"post_id", "display_order"})
})
public class PostImage {

    // post_images 스키마엔 updated_at이 없어 BaseSoftDeleteEntity 대신 created_at/deleted_at만 매핑한다(V1__init.sql).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "alt_text", length = 255)
    private String altText;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private PostImage(Post post, String imageUrl, String altText, Integer displayOrder) {
        this.post = post;
        this.imageUrl = imageUrl;
        this.altText = altText;
        this.displayOrder = displayOrder;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public static PostImage create(Post post, String imageUrl, String altText, Integer displayOrder) {
        return PostImage.builder()
            .post(post)
            .imageUrl(imageUrl)
            .altText(altText)
            .displayOrder(displayOrder)
            .build();
    }

    public void update(String imageUrl, String altText, Integer displayOrder) {
        this.imageUrl = imageUrl;
        this.altText = altText;
        this.displayOrder = displayOrder;
    }
}
