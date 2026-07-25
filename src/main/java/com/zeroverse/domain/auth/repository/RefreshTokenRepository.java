package com.zeroverse.domain.auth.repository;

import com.zeroverse.domain.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Refresh Token 저장소(ADR-0003). */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * rotation·signout 전용 조회. {@code PESSIMISTIC_WRITE}로 row를 잠가 동시 갱신 중 하나만
     * 성공하도록 한다(ADR-0003 §2). 잠금 없이 조회하면 두 요청이 같은 row를 각자 revoke하고
     * 각자 새 토큰을 발급해 세션이 갈라진다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.tokenId = :tokenId")
    Optional<RefreshToken> findByTokenIdForUpdate(@Param("tokenId") String tokenId);

    Optional<RefreshToken> findByTokenId(String tokenId);
}
