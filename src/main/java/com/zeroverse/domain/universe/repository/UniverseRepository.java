package com.zeroverse.domain.universe.repository;

import com.zeroverse.domain.universe.entity.Universe;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UniverseRepository extends JpaRepository<Universe, Long> {
    boolean existsByFromUserIdAndToUserIdAndStatus(
            Long fromUserId, Long toUserId, UniverseStatus status);

    /** 사용자 행 잠금 이후 관계의 최신 상태를 읽는 current locking read. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Universe u where u.fromUser.id = :fromUserId "
            + "and u.toUser.id = :toUserId and u.status = :status")
    java.util.List<Universe> findCurrentByFromUserIdAndToUserIdAndStatusForUpdate(
            @Param("fromUserId") Long fromUserId,
            @Param("toUserId") Long toUserId,
            @Param("status") UniverseStatus status);

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

    /** M6 mutation용 current pair read. 사용자 lock 뒤 Universe 행도 잠가 stale snapshot을 피한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Universe u "
            + "where (u.fromUser.id = :firstId and u.toUser.id = :secondId) "
            + "or (u.fromUser.id = :secondId and u.toUser.id = :firstId)")
    java.util.List<Universe> findPairForUpdate(@Param("firstId") Long firstId, @Param("secondId") Long secondId);

    @Query("select u from Universe u "
            + "where u.status = com.zeroverse.domain.universe.entity.UniverseStatus.BLOCKED "
            + "and ((u.fromUser.id = :actorId and u.toUser.id in :targetIds) "
            + "or (u.toUser.id = :actorId and u.fromUser.id in :targetIds))")
    java.util.List<Universe> findBlockedBetween(
            @Param("actorId") Long actorId, @Param("targetIds") java.util.Collection<Long> targetIds);

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
