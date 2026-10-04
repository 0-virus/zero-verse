package com.zeroverse.domain.upload.repository;

import com.zeroverse.domain.upload.entity.ImageUpload;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImageUploadRepository extends JpaRepository<ImageUpload, UUID> {

    Optional<ImageUpload> findByImageUrl(String imageUrl);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ImageUpload i join fetch i.owner left join fetch i.boundPost where i.id = :id")
    Optional<ImageUpload> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ImageUpload i join fetch i.owner left join fetch i.boundPost where i.imageUrl = :imageUrl")
    Optional<ImageUpload> findByImageUrlForUpdate(@Param("imageUrl") String imageUrl);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ImageUpload i where i.boundPost.id = :postId and i.detachedAt is null order by i.id")
    List<ImageUpload> findCurrentlyBoundByPostId(@Param("postId") Long postId);
}
