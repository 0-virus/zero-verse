package com.zeroverse.domain.user.repository;

import com.zeroverse.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 조회(FR-AUTH-01·02, FR-SETTINGS-01).
 *
 * <p>soft delete된 사용자는 조회 결과에서 제외한다 — 삭제된 계정은 "없는 계정"과 동일하게 취급한다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    /**
     * 자신을 제외한 다른 사용자의 nickname 중복 검사(FR-SETTINGS-01).
     *
     * <p>사용자가 프로필을 업데이트할 때 자신의 기존 nickname을 그대로 두고 다른 필드만
     * 바꾸는 경우, "자신의 nickname도 중복이다"고 거부하면 안 된다. 이 메서드는
     * {@code userId}를 제외한 다른 사용자의 nickname만 검사한다.
     *
     * @param nickname 검사할 nickname
     * @param userId 자신의 사용자 ID (제외 대상)
     * @return soft delete되지 않은 다른 사용자가 이미 사용 중이면 true
     */
    boolean existsByNicknameAndIdNotAndDeletedAtIsNull(String nickname, Long userId);
}
