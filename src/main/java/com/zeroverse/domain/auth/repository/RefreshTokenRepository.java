package com.zeroverse.domain.auth.repository;

import com.zeroverse.domain.auth.entity.RefreshToken;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenId = :tokenId")
    Optional<RefreshToken> findByTokenId(@Param("tokenId") String tokenId);

    /**
     * 사용자의 활성(미폐기 + 미만료) refresh token을 최신순으로 조회한다.
     * 만료 기준 시각은 DB timezone 영향을 피하기 위해 호출측 {@code now}로 주입하며,
     * 최신 1건만 필요할 때는 {@code Pageable}로 limit 한다(여러 active 토큰 존재 가능).
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user.id = :userId AND rt.revokedAt IS NULL AND rt.expiresAt > :now ORDER BY rt.createdAt DESC, rt.id DESC")
    List<RefreshToken> findActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now, Pageable pageable);
}
