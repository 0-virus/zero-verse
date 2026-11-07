package com.zeroverse.domain.universe.repository;

import com.zeroverse.domain.universe.entity.Universe;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UniverseRepository extends JpaRepository<Universe, Long> {
    boolean existsByFromUserIdAndToUserIdAndStatus(
            Long fromUserId, Long toUserId, UniverseStatus status);

    Optional<Universe> findByFromUserIdAndToUserId(Long fromUserId, Long toUserId);

    @EntityGraph(attributePaths = {"fromUser", "toUser"})
    @Query("select u from Universe u join u.fromUser fromUser join u.toUser toUser "
            + "where u.id = :id and fromUser.deletedAt is null and toUser.deletedAt is null")
    Optional<Universe> findVisibleById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"fromUser", "toUser"})
    @Query("select u from Universe u join u.fromUser fromUser join u.toUser toUser "
            + "where (u.fromUser.id = :firstId and u.toUser.id = :secondId) "
            + "or (u.fromUser.id = :secondId and u.toUser.id = :firstId)")
    java.util.List<Universe> findPair(@Param("firstId") Long firstId, @Param("secondId") Long secondId);

    @EntityGraph(attributePaths = {"fromUser", "toUser"})
    @Query("select u from Universe u join u.fromUser fromUser join u.toUser toUser "
            + "where u.toUser.id = :userId and u.status = :status "
            + "and fromUser.deletedAt is null and toUser.deletedAt is null "
            + "and (:nickname is null or lower(fromUser.nickname) like lower(concat('%', :nickname, '%')))")
    Page<Universe> findReceived(@Param("userId") Long userId, @Param("status") UniverseStatus status,
            @Param("nickname") String nickname, Pageable pageable);

    @EntityGraph(attributePaths = {"fromUser", "toUser"})
    @Query("select u from Universe u join u.fromUser fromUser join u.toUser toUser "
            + "where u.fromUser.id = :userId and u.status = :status "
            + "and fromUser.deletedAt is null and toUser.deletedAt is null "
            + "and (:nickname is null or lower(toUser.nickname) like lower(concat('%', :nickname, '%')))")
    Page<Universe> findSent(@Param("userId") Long userId, @Param("status") UniverseStatus status,
            @Param("nickname") String nickname, Pageable pageable);

    @EntityGraph(attributePaths = {"fromUser", "toUser"})
    @Query("select u from Universe u join u.fromUser fromUser join u.toUser toUser "
            + "where u.fromUser.id = :userId and u.status = :status "
            + "and fromUser.deletedAt is null and toUser.deletedAt is null "
            + "and (:nickname is null or lower(toUser.nickname) like lower(concat('%', :nickname, '%')))")
    Page<Universe> findFollowing(@Param("userId") Long userId, @Param("status") UniverseStatus status,
            @Param("nickname") String nickname, Pageable pageable);

    @EntityGraph(attributePaths = {"fromUser", "toUser"})
    @Query("select u from Universe u join u.fromUser fromUser join u.toUser toUser "
            + "where u.toUser.id = :userId and u.status = :status "
            + "and fromUser.deletedAt is null and toUser.deletedAt is null "
            + "and (:nickname is null or lower(fromUser.nickname) like lower(concat('%', :nickname, '%')))")
    Page<Universe> findFollowers(@Param("userId") Long userId, @Param("status") UniverseStatus status,
            @Param("nickname") String nickname, Pageable pageable);

    @EntityGraph(attributePaths = {"fromUser", "toUser"})
    @Query("select u from Universe u join u.fromUser fromUser join u.toUser toUser "
            + "where u.fromUser.id = :userId and u.status = :status "
            + "and fromUser.deletedAt is null and toUser.deletedAt is null "
            + "and exists (select reverse.id from Universe reverse "
            + "where reverse.fromUser.id = u.toUser.id and reverse.toUser.id = u.fromUser.id "
            + "and reverse.status = :status) "
            + "and (:nickname is null or lower(toUser.nickname) like lower(concat('%', :nickname, '%')))")
    Page<Universe> findFriends(@Param("userId") Long userId, @Param("status") UniverseStatus status,
            @Param("nickname") String nickname, Pageable pageable);

    @EntityGraph(attributePaths = {"fromUser", "toUser"})
    @Query("select u from Universe u join u.fromUser fromUser join u.toUser toUser "
            + "where u.fromUser.id = :userId and u.status = :status "
            + "and fromUser.deletedAt is null and toUser.deletedAt is null "
            + "and (:nickname is null or lower(toUser.nickname) like lower(concat('%', :nickname, '%')))")
    Page<Universe> findBlocks(@Param("userId") Long userId, @Param("status") UniverseStatus status,
            @Param("nickname") String nickname, Pageable pageable);

}
