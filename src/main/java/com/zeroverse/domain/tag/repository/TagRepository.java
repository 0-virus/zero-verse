package com.zeroverse.domain.tag.repository;

import com.zeroverse.domain.tag.entity.Tag;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByNormalizedName(String normalizedName);
}
