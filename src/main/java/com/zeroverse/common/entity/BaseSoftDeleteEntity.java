package com.zeroverse.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;

/**
 * soft delete가 필요한 엔티티의 공통 상위 클래스.
 *
 * <p>{@code deletedAt}이 null이 아니면 삭제된 것으로 본다. 조회 시 자동 제외 필터는 도메인 Repository가
 * 도입되는 마일스톤에서 각 Repository가 책임진다(M0 범위 밖).
 */
@MappedSuperclass
public abstract class BaseSoftDeleteEntity extends BaseEntity {

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.deletedAt = null;
    }
}
