package com.zeroverse.domain.post.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "post_tags", uniqueConstraints = {
    @UniqueConstraint(name = "uk_post_tag", columnNames = {"post_id", "tag_id"})
})
public class PostTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @Builder
    private PostTag(Post post, Tag tag) {
        this.post = post;
        this.tag = tag;
    }

    public static PostTag create(Post post, Tag tag) {
        return PostTag.builder()
            .post(post)
            .tag(tag)
            .build();
    }
}
