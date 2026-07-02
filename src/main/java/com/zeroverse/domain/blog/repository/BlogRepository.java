package com.zeroverse.domain.blog.repository;

import com.zeroverse.domain.blog.entity.Blog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BlogRepository extends JpaRepository<Blog, Long> {

    @Query("SELECT b FROM Blog b WHERE b.user.id = :userId AND b.deletedAt IS NULL ORDER BY b.id ASC LIMIT 1")
    Optional<Blog> findDefaultByUserId(@Param("userId") Long userId);

    @Query("SELECT b FROM Blog b WHERE b.urlSlug = :urlSlug AND b.deletedAt IS NULL")
    Optional<Blog> findByUrlSlug(@Param("urlSlug") String urlSlug);

    @Query("SELECT EXISTS(SELECT 1 FROM Blog b WHERE b.urlSlug = :urlSlug AND b.deletedAt IS NULL)")
    boolean existsByUrlSlug(@Param("urlSlug") String urlSlug);

    @Query("SELECT EXISTS(SELECT 1 FROM Blog b WHERE b.urlSlug = :urlSlug AND b.deletedAt IS NULL AND b.user.id = :userId)") boolean existsByUrlSlugAndUserId(@Param("urlSlug") String urlSlug, @Param("userId") Long userId);
}
