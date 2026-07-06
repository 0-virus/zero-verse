package com.zeroverse.domain.post.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.PostImage;
import com.zeroverse.domain.post.entity.PostTag;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.post.repository.PostImageRepository;
import com.zeroverse.domain.post.repository.PostRepository;
import com.zeroverse.domain.post.repository.PostTagRepository;
import com.zeroverse.domain.post.util.HtmlSanitizer;
import com.zeroverse.domain.post.util.TagNormalizer;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.dto.post.*;
import com.querydsl.core.BooleanBuilder;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class PostService {

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final PostTagRepository postTagRepository;
    private final BlogRepository blogRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final TagService tagService;
    private final PostAccessControlService accessControlService;
    private final ViewCountGuard viewCountGuard;
    private final EntityManager entityManager;

    public PostService(PostRepository postRepository,
                       PostImageRepository postImageRepository,
                       PostTagRepository postTagRepository,
                       BlogRepository blogRepository,
                       CategoryRepository categoryRepository,
                       UserRepository userRepository,
                       TagService tagService,
                       PostAccessControlService accessControlService,
                       ViewCountGuard viewCountGuard,
                       EntityManager entityManager) {
        this.postRepository = postRepository;
        this.postImageRepository = postImageRepository;
        this.postTagRepository = postTagRepository;
        this.blogRepository = blogRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.tagService = tagService;
        this.accessControlService = accessControlService;
        this.viewCountGuard = viewCountGuard;
        this.entityManager = entityManager;
    }

    /**
     * 게시글 생성.
     */
    public PostDetailResponse createPost(Long userId, CreatePostRequest request) {
        // blog 검증 및 소유권 확인
        Blog blog = blogRepository.findById(request.blogId())
            .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

        if (!blog.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.POST_003);
        }

        // category 검증 (동일 blog에 속하는지)
        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CAT_001));

            if (!category.getBlog().getId().equals(request.blogId()) || category.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.CAT_001);
            }
        }

        // visibility 검증
        if (request.visibility() == null) {
            throw new BusinessException(ErrorCode.POST_005);
        }

        // 이미지 검증
        validateImages(request.images());

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        // HTML sanitize
        String sanitizedHtml = HtmlSanitizer.sanitize(request.contentHtml());

        // Post 생성
        Post post = Post.create(
            user,
            blog,
            category,
            request.title(),
            request.contentJson(),
            sanitizedHtml,
            request.thumbnailUrl(),
            request.visibility(),
            request.publish() != null && request.publish()
        );

        Post saved = postRepository.save(post);

        // Tag 동기화
        syncTags(saved, request.tagNames());

        // PostImage 동기화
        syncImages(saved, request.images());

        // Flush and clear to ensure fresh load from DB
        entityManager.flush();
        entityManager.detach(saved);

        // Reload to get tags and images
        Post reloaded = postRepository.findByIdWithDetails(saved.getId()).get();
        return PostDetailResponse.from(reloaded);
    }

    /**
     * 게시글 수정.
     */
    public PostDetailResponse updatePost(Long postId, Long userId, UpdatePostRequest request) {
        Post post = postRepository.findByIdWithDetails(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_001));

        // 작성자 검증
        accessControlService.canModify(post, userId);

        // category 검증
        Category newCategory = null;
        if (request.categoryId() != null) {
            newCategory = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CAT_001));

            if (!newCategory.getBlog().getId().equals(post.getBlog().getId()) || newCategory.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.CAT_001);
            }
        }

        // visibility 검증
        if (request.visibility() == null) {
            throw new BusinessException(ErrorCode.POST_005);
        }

        // 이미지 검증
        validateImages(request.images());

        // HTML sanitize
        String sanitizedHtml = HtmlSanitizer.sanitize(request.contentHtml());

        // Post 수정
        post.update(
            newCategory,
            request.title(),
            request.contentJson(),
            sanitizedHtml,
            request.thumbnailUrl(),
            request.visibility(),
            request.publish() != null && request.publish()
        );

        Post updated = postRepository.save(post);

        // Tag 재동기화
        syncTags(updated, request.tagNames());

        // Image 재동기화
        syncImages(updated, request.images());

        // Flush and clear to ensure fresh load from DB
        entityManager.flush();
        entityManager.detach(updated);

        // Reload to get updated tags and images
        Post reloaded = postRepository.findByIdWithDetails(updated.getId()).get();
        return PostDetailResponse.from(reloaded);
    }

    /**
     * 게시글 삭제 (soft delete).
     */
    public DeletePostResponse deletePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_001));

        // 작성자 검증
        accessControlService.canModify(post, userId);

        // soft delete
        post.softDelete();
        postRepository.save(post);

        // PostImage도 soft delete
        List<PostImage> images = postImageRepository.findActiveByPostIdOrderByDisplayOrder(postId);
        images.forEach(PostImage::softDelete);
        postImageRepository.saveAll(images);

        return new DeletePostResponse(post.getId(), post.getDeletedAt());
    }

    /**
     * 게시글 상세 조회 및 조회수 증가.
     * 조회수 증가는 쓰기 작업이므로 readOnly=false로 설정.
     */
    @Transactional
    public PostDetailResponse getPost(Long postId, Long userId, String ipHash) {
        Post post = postRepository.findByIdWithDetails(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_001));

        // 접근 제어
        accessControlService.canView(post, userId);

        // 조회수 증가 (guard check)
        if (viewCountGuard.canIncrement(postId, userId, ipHash)) {
            post.incrementViewCount();
            postRepository.save(post);
        }

        return PostDetailResponse.from(post);
    }

    /**
     * 게시글 목록 조회 (블로그별).
     */
    @Transactional(readOnly = true)
    public Page<PostListItemResponse> getPostsByBlog(Long blogId, Long categoryId, String tag,
                                                      Visibility visibility, Boolean published,
                                                      Pageable pageable, Long userId) {
        // blog 존재 여부 확인
        Blog blog = blogRepository.findById(blogId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

        if (blog.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.BLOG_001);
        }

        // category 검증 (같은 blog인지)
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAT_001));

            if (!category.getBlog().getId().equals(blogId) || category.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.CAT_001);
            }
        }

        // QueryDSL 필터 조합
        BooleanBuilder builder = new BooleanBuilder();
        com.zeroverse.domain.post.entity.QPost qPost = com.zeroverse.domain.post.entity.QPost.post;

        // soft delete 제외
        builder.and(qPost.deletedAt.isNull());

        // blog 필터
        builder.and(qPost.blog.id.eq(blogId));

        // category 필터
        if (categoryId != null) {
            builder.and(qPost.category.id.eq(categoryId));
        }

        // 접근제어: 승인 매트릭스 적용
        boolean isOwner = userId != null && blog.getUser().getId().equals(userId);
        if (!isOwner) {
            // 비소유자: 발행된 글만
            builder.and(qPost.publishedAt.isNotNull());

            // 익명(userId=null): PUBLIC만
            // 로그인 비소유자: PUBLIC 또는 UNIVERSE
            if (userId == null) {
                builder.and(qPost.visibility.eq(Visibility.PUBLIC));
            } else {
                builder.and(qPost.visibility.in(Visibility.PUBLIC, Visibility.UNIVERSE));
            }
        } else {
            // 소유자: visibility 필터 적용 (없으면 모든 visibility)
            if (visibility != null) {
                builder.and(qPost.visibility.eq(visibility));
            }

            // 소유자: published 필터 적용
            if (published != null) {
                if (published) {
                    builder.and(qPost.publishedAt.isNotNull());
                } else {
                    builder.and(qPost.publishedAt.isNull());
                }
            }
        }

        // tag 필터
        if (tag != null && !tag.isEmpty()) {
            String normalizedTag = TagNormalizer.normalizeSingle(tag);
            builder.and(qPost.postTags.any().tag.normalizedName.eq(normalizedTag));
        }

        // 정렬: publishedAt desc
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.DESC, "publishedAt"
        );

        Page<Post> posts = postRepository.findAll(builder, PageRequest.of(
            pageable.getPageNumber(),
            Math.min(pageable.getPageSize(), 100),
            sort
        ));

        return posts.map(PostListItemResponse::from);
    }

    /**
     * 임시저장 목록 조회.
     */
    @Transactional(readOnly = true)
    public Page<PostListItemResponse> getDrafts(Long userId, Pageable pageable) {
        // QueryDSL: publishedAt IS NULL AND user_id = userId, ordered by updatedAt DESC
        BooleanBuilder builder = new BooleanBuilder();
        com.zeroverse.domain.post.entity.QPost qPost = com.zeroverse.domain.post.entity.QPost.post;

        builder.and(qPost.deletedAt.isNull());
        builder.and(qPost.publishedAt.isNull());
        builder.and(qPost.user.id.eq(userId));

        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"
        );

        Page<Post> posts = postRepository.findAll(builder, PageRequest.of(
            pageable.getPageNumber(),
            Math.min(pageable.getPageSize(), 100),
            sort
        ));

        return posts.map(PostListItemResponse::from);
    }

    /**
     * 태그별 게시글 목록 조회.
     */
    @Transactional(readOnly = true)
    public Page<PostListItemResponse> getPostsByTag(String tagName, Pageable pageable, Long userId) {
        String normalized = TagNormalizer.normalizeSingle(tagName);

        // QueryDSL: post_tags.tag.normalized_name = normalized, published only, access control applied
        BooleanBuilder builder = new BooleanBuilder();
        com.zeroverse.domain.post.entity.QPost qPost = com.zeroverse.domain.post.entity.QPost.post;

        builder.and(qPost.deletedAt.isNull());
        builder.and(qPost.publishedAt.isNotNull()); // published only
        builder.and(qPost.postTags.any().tag.normalizedName.eq(normalized));

        // visibility 필터 (PUBLIC 또는 로그인 사용자면 UNIVERSE도 허용)
        if (userId != null) {
            // 로그인 사용자: PUBLIC 또는 UNIVERSE 발행 글
            builder.and(qPost.visibility.in(Visibility.PUBLIC, Visibility.UNIVERSE));
        } else {
            // 비로그인: PUBLIC만
            builder.and(qPost.visibility.eq(Visibility.PUBLIC));
        }

        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.DESC, "publishedAt"
        );

        Page<Post> posts = postRepository.findAll(builder, PageRequest.of(
            pageable.getPageNumber(),
            Math.min(pageable.getPageSize(), 100),
            sort
        ));

        return posts.map(PostListItemResponse::from);
    }

    /**
     * PostImage 목록 동기화 (PUT).
     */
    public PostImagesResponse syncImages(Long postId, Long userId, UpdatePostImagesRequest request) {
        Post post = postRepository.findByIdWithDetails(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_001));

        // 작성자 검증
        accessControlService.canModify(post, userId);

        // 이미지 요청 검증 (일관화된 검증)
        validateImages(request.images());

        syncImages(post, request.images());
        Post updated = postRepository.save(post);

        // Flush and detach to ensure fresh load from DB
        entityManager.flush();
        entityManager.detach(updated);

        // Reload to get updated images
        Post reloaded = postRepository.findByIdWithDetails(updated.getId()).get();
        return PostImagesResponse.from(reloaded);
    }

    /**
     * Tag 동기화 헬퍼.
     */
    private void syncTags(Post post, List<String> tagNames) {
        // 기존 PostTag 제거
        List<PostTag> existing = postTagRepository.findByPostId(post.getId());
        postTagRepository.deleteAll(existing);

        // 새 태그 추가
        if (tagNames == null || tagNames.isEmpty()) {
            return;
        }

        List<String> normalized = TagNormalizer.normalize(tagNames);
        for (String normalized_name : normalized) {
            // 원본 이름은 첫 입력된 것 사용 (또는 normalized_name)
            com.zeroverse.domain.post.entity.Tag tag = tagService.findOrCreate(normalized_name, normalized_name);
            PostTag postTag = PostTag.create(post, tag);
            postTagRepository.save(postTag);
        }
    }

    /**
     * Image 동기화 헬퍼.
     * soft delete 사용으로 unique(post_id, display_order) 충돌 가능성이 있으므로:
     * 1. 기존 이미지의 displayOrder를 오프셋(+10000)
     * 2. soft delete (deleted_at 세팅)
     * 3. 새 이미지 추가
     */
    private void syncImages(Post post, List<PostImageRequest> images) {
        // 기존 이미지의 displayOrder를 오프셋하여 unique constraint 회피
        postImageRepository.offsetDisplayOrderByPostId(post.getId());

        // 기존 이미지 soft delete
        postImageRepository.deleteActiveByPostId(post.getId());

        // DB에 반영
        entityManager.flush();

        // 새 이미지 추가
        if (images == null || images.isEmpty()) {
            return;
        }

        for (PostImageRequest imgReq : images) {
            PostImage image = PostImage.create(post, imgReq.imageUrl(), imgReq.altText(), imgReq.displayOrder());
            postImageRepository.save(image);
        }
    }

    /**
     * 이미지 요청 검증 (create/update/sync 공통).
     * - 빈 imageUrl 제거
     * - null displayOrder 제거
     * - 중복된 displayOrder 제거
     */
    private void validateImages(List<PostImageRequest> images) {
        if (images == null || images.isEmpty()) {
            return;
        }

        Set<Integer> seenOrders = new HashSet<>();
        for (PostImageRequest imgReq : images) {
            if (imgReq.imageUrl() == null || imgReq.imageUrl().isEmpty()) {
                throw new BusinessException(ErrorCode.POST_006);
            }
            if (imgReq.displayOrder() == null) {
                throw new BusinessException(ErrorCode.POST_006);
            }
            // 중복 displayOrder 검사
            if (!seenOrders.add(imgReq.displayOrder())) {
                throw new BusinessException(ErrorCode.POST_006);
            }
        }
    }
}
