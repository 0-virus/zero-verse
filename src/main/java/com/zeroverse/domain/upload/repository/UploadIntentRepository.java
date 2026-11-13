package com.zeroverse.domain.upload.repository;

import com.zeroverse.domain.upload.UploadIntentStatus;
import com.zeroverse.domain.upload.entity.UploadIntent;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UploadIntentRepository extends JpaRepository<UploadIntent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from UploadIntent i join fetch i.owner where i.id = :id")
    Optional<UploadIntent> findByIdForUpdate(@Param("id") UUID id);

    List<UploadIntent> findByStatusInAndExpiresAtLessThanEqualAndUploadExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
            List<UploadIntentStatus> statuses,
            LocalDateTime expiresAt,
            LocalDateTime uploadExpiresAt,
            Pageable pageable);
}
