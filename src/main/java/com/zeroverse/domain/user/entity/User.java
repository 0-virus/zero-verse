package com.zeroverse.domain.user.entity;

import com.zeroverse.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "users"
)
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false, unique = true, length = 63)
    private String email;
    @Column(nullable = false, length = 63)
    private String password;
    @Column(nullable = false, length = 20)
    private String name;
    @Column(nullable = false, unique = true, length = 20)
    private String nickname;
    private LocalDate birthDate;
    @Column(columnDefinition = "TEXT")
    private String bio;
    private String profileImageUrl;
}