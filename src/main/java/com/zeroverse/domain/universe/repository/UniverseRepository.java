package com.zeroverse.domain.universe.repository;

import com.zeroverse.domain.universe.entity.Universe;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UniverseRepository extends JpaRepository<Universe, Long> {
    boolean existsByFromUserIdAndToUserIdAndStatus(
            Long fromUserId, Long toUserId, UniverseStatus status);
}
