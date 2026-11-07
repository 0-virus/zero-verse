package com.zeroverse.domain.universe.entity;

import com.zeroverse.common.entity.BaseEntity;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 유니버스 단방향 관계 행. 거절·취소·삭제·차단 해제는 행을 hard-delete한다. */
@Entity
@Table(name = "universes")
public class Universe extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UniverseStatus status;

    protected Universe() {}

    private Universe(User fromUser, User toUser, UniverseStatus status) {
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.status = status;
    }

    public static Universe pending(User fromUser, User toUser) {
        return new Universe(fromUser, toUser, UniverseStatus.PENDING);
    }

    public static Universe blocked(User fromUser, User toUser) {
        return new Universe(fromUser, toUser, UniverseStatus.BLOCKED);
    }

    public Long getId() { return id; }
    public User getFromUser() { return fromUser; }
    public User getToUser() { return toUser; }
    public UniverseStatus getStatus() { return status; }

    public void accept() {
        if (status != UniverseStatus.PENDING) {
            throw new BusinessException(ErrorCode.UNI_005);
        }
        this.status = UniverseStatus.ACCEPTED;
    }
}
