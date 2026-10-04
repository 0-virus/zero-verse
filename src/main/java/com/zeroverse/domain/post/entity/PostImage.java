package com.zeroverse.domain.post.entity;

import com.zeroverse.common.entity.BaseSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 게시글 본문 이미지 snapshot. 삭제 이력은 soft delete로 보존한다. */
@Entity
@Table(name = "post_images")
public class PostImage extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "alt_text", length = 255)
    private String altText;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    protected PostImage() {}

    private PostImage(Post post, String imageUrl, String altText, Integer displayOrder) {
        this.post = post;
        this.imageUrl = imageUrl;
        this.altText = altText;
        this.displayOrder = displayOrder;
    }

    public static PostImage create(Post post, String imageUrl, String altText, Integer displayOrder) {
        return new PostImage(post, imageUrl, altText, displayOrder);
    }

    public Long getId() { return id; }
    public Post getPost() { return post; }
    public String getImageUrl() { return imageUrl; }
    public String getAltText() { return altText; }
    public Integer getDisplayOrder() { return displayOrder; }
}
