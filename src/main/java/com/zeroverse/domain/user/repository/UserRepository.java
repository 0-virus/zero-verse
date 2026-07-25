package com.zeroverse.domain.user.repository;

import com.zeroverse.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 조회(FR-AUTH-01·02).
 *
 * <p>soft delete된 사용자는 조회 결과에서 제외한다 — 삭제된 계정은 "없는 계정"과 동일하게 취급한다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByNicknameAndDeletedAtIsNull(String nickname);
}
