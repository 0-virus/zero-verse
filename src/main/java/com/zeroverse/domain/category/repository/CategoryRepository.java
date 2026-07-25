package com.zeroverse.domain.category.repository;

import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 카테고리 조회. M1은 가입 시 미분류 생성까지만 쓴다(트리 CRUD는 M3). */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findFirstByBlogIdAndTypeAndDeletedAtIsNull(Long blogId, CategoryType type);
}
