package com.zeroverse.domain.user.repository;

import com.zeroverse.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.nickname = :nickname AND u.deletedAt IS NULL")
    Optional<User> findByNickname(@Param("nickname") String nickname);

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findById(@Param("id") Long id);

    @Query("SELECT EXISTS(SELECT 1 FROM User u WHERE u.email = :email AND u.deletedAt IS NULL)")
    boolean existsByEmail(@Param("email") String email);

    @Query("SELECT EXISTS(SELECT 1 FROM User u WHERE u.nickname = :nickname AND u.deletedAt IS NULL)")
    boolean existsByNickname(@Param("nickname") String nickname);
}
