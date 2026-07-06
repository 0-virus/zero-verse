package com.zeroverse.domain.post.service;

import com.zeroverse.domain.post.entity.Tag;
import com.zeroverse.domain.post.repository.TagRepository;
import com.zeroverse.domain.post.util.TagNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 태그 관리 서비스.
 */
@Service
@Transactional
public class TagService {

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /**
     * 정규화된 태그명으로 Tag를 찾거나 없으면 생성.
     *
     * @param normalizedName 정규화된 태그명 (lowercase, trim)
     * @param originalName 원본 태그명
     * @return Tag 엔티티
     */
    public Tag findOrCreate(String normalizedName, String originalName) {
        return tagRepository.findByNormalizedName(normalizedName)
            .orElseGet(() -> {
                Tag newTag = Tag.create(originalName, normalizedName);
                return tagRepository.save(newTag);
            });
    }
}
