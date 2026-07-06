package com.zeroverse.domain.post.repository;

import com.zeroverse.domain.post.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByNormalizedName(String normalizedName);
}
