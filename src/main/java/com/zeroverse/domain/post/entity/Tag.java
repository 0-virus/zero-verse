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
@Table(name = "tags", indexes = {
    @Index(name = "idx_normalized_name", columnList = "normalized_name")
})
public class Tag {

    // tags 스키마엔 updated_at이 없어 BaseEntity를 상속하지 않고 created_at만 매핑한다(V1__init.sql).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 100, unique = true)
    private String normalizedName;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Tag(String name, String normalizedName) {
        this.name = name;
        this.normalizedName = normalizedName;
    }

    public static Tag create(String name, String normalizedName) {
        return Tag.builder()
            .name(name)
            .normalizedName(normalizedName)
            .build();
    }
}
