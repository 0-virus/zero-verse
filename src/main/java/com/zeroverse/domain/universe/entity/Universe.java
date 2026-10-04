package com.zeroverse.domain.universe.entity;

import com.zeroverse.common.entity.BaseEntity;
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

/** M4가 읽기 전용으로 소비하는 기존 Universe 관계 행. CRUD는 M5 범위다. */
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

    public Long getId() { return id; }
    public User getFromUser() { return fromUser; }
    public User getToUser() { return toUser; }
    public UniverseStatus getStatus() { return status; }
}
