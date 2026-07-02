package com.zeroverse.domain.blog.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.util.SlugGenerator;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.dto.blog.BlogInitialSetupRequest;
import com.zeroverse.dto.blog.BlogPublicResponse;
import com.zeroverse.dto.blog.BlogSettingsRequest;
import com.zeroverse.dto.blog.BlogSettingsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class BlogService {
    private final BlogRepository blogRepository;
    private final UserRepository userRepository;

    public BlogService(BlogRepository blogRepository, UserRepository userRepository) {
        this.blogRepository = blogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public BlogSettingsResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        Blog blog = blogRepository.findDefaultByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

        return BlogSettingsResponse.from(blog);
    }

    public BlogSettingsResponse updateBlog(Long userId, BlogSettingsRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        Blog blog = blogRepository.findDefaultByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

        // Check if blog setup is completed
        if (!blog.getIsSetupCompleted()) {
            throw new BusinessException(ErrorCode.BLOG_004);
        }

        // Check URL slug uniqueness if slug is being changed
        if (request.urlSlug() != null && !request.urlSlug().equals(blog.getUrlSlug())) {
            validateSlug(request.urlSlug());
            if (blogRepository.existsByUrlSlug(request.urlSlug())) {
                throw new BusinessException(ErrorCode.BLOG_002);
            }
        }

        blog.updateBlogInfo(
            request.title(),
            request.urlSlug(),
            request.description()
        );

        Blog updatedBlog = blogRepository.save(blog);
        return BlogSettingsResponse.from(updatedBlog);
    }

    public BlogSettingsResponse initialSetup(Long userId, BlogInitialSetupRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        Blog blog = blogRepository.findDefaultByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

        // Check if already setup
        if (blog.getIsSetupCompleted()) {
            throw new BusinessException(ErrorCode.BLOG_004);
        }

        // Prepare values with defaults
        String title = request.title() != null && !request.title().isBlank()
            ? request.title()
            : user.getNickname() + "의 블로그";

        String urlSlug = request.urlSlug() != null && !request.urlSlug().isBlank()
            ? request.urlSlug()
            : blog.getUrlSlug(); // Use existing blog's slug if not provided

        // Validate URL slug format
        validateSlug(urlSlug);

        // Check URL slug uniqueness only if slug is changing
        if (!urlSlug.equals(blog.getUrlSlug()) && blogRepository.existsByUrlSlug(urlSlug)) {
            throw new BusinessException(ErrorCode.BLOG_002);
        }

        String description = request.description() != null ? request.description() : null;

        // Setup blog
        blog.setupBlog(title, urlSlug, description);

        Blog updatedBlog = blogRepository.save(blog);
        return BlogSettingsResponse.from(updatedBlog);
    }

    @Transactional(readOnly = true)
    public BlogPublicResponse getPublicBlog(String urlSlug) {
        Blog blog = blogRepository.findByUrlSlug(urlSlug)
            .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

        // Check if user is soft deleted
        if (blog.getUser().isDeleted()) {
            throw new BusinessException(ErrorCode.BLOG_001);
        }

        return BlogPublicResponse.from(blog);
    }

    private void validateSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new BusinessException(ErrorCode.BLOG_003);
        }

        // Validate slug format using SlugGenerator rules
        String normalized = SlugGenerator.normalize(slug);
        if (!normalized.equals(slug)) {
            throw new BusinessException(ErrorCode.BLOG_003);
        }
    }
}
