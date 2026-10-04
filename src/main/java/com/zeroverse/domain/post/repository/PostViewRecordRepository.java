package com.zeroverse.domain.post.repository;

import com.zeroverse.domain.post.entity.PostViewRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PostViewRecordRepository extends JpaRepository<PostViewRecord, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PostViewRecord r where r.post.id = :postId and r.viewerKey = :viewerKey")
    Optional<PostViewRecord> findForUpdate(
            @Param("postId") Long postId, @Param("viewerKey") String viewerKey);
}
