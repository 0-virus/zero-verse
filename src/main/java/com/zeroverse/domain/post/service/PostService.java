package com.zeroverse.domain.post.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.PageResponse;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.post.dto.PostDtos;
import com.zeroverse.domain.post.dto.PostDtos.AdjacentPost;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.dto.PostDtos.PostAuthor;
import com.zeroverse.domain.post.dto.PostDtos.PostCategory;
import com.zeroverse.domain.post.dto.PostDtos.PostDetail;
import com.zeroverse.domain.post.dto.PostDtos.PostImageInput;
import com.zeroverse.domain.post.dto.PostDtos.PostSummary;
import com.zeroverse.domain.post.dto.PostDtos.UpdatePostRequest;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.PostImage;
import com.zeroverse.domain.post.entity.PostViewRecord;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.post.repository.PostImageRepository;
import com.zeroverse.domain.post.repository.PostRepository;
import com.zeroverse.domain.post.repository.PostViewRecordRepository;
import com.zeroverse.domain.tag.entity.PostTag;
import com.zeroverse.domain.tag.entity.Tag;
import com.zeroverse.domain.tag.repository.PostTagRepository;
import com.zeroverse.domain.tag.repository.TagRepository;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.domain.upload.service.UploadService;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게시글 CRUD·목록·조회수와 tag/PostImage snapshot 동기화. */
@Service
public class PostService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_TAGS = 10;
    private static final int MAX_TAG_LENGTH = 100;
    private static final int MAX_IMAGES = 100;
    private static final int VIEW_RECORD_CLEANUP_BATCH_SIZE = 500;
    private static final Duration VIEW_RECORD_RETENTION = Duration.ofHours(48);

    private final BlogRepository blogRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final PostViewRecordRepository postViewRecordRepository;
    private final PostTagRepository postTagRepository;
    private final TagRepository tagRepository;
    private final PostAccessPolicy accessPolicy;
    private final PostContentService contentService;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final Clock clock;
    private final UploadService uploadService;
    private final String viewerHmacSecret;

    public PostService(
            BlogRepository blogRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            PostRepository postRepository,
            PostImageRepository postImageRepository,
            PostViewRecordRepository postViewRecordRepository,
            PostTagRepository postTagRepository,
            TagRepository tagRepository,
            PostAccessPolicy accessPolicy,
            PostContentService contentService,
            JdbcTemplate jdbcTemplate,
            EntityManager entityManager,
            Clock clock,
            UploadService uploadService,
            @Value("${zeroverse.upload.viewer-hmac-secret}")
                    String viewerHmacSecret) {
        this.blogRepository = blogRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.postRepository = postRepository;
        this.postImageRepository = postImageRepository;
        this.postViewRecordRepository = postViewRecordRepository;
        this.postTagRepository = postTagRepository;
        this.tagRepository = tagRepository;
        this.accessPolicy = accessPolicy;
        this.contentService = contentService;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
        this.clock = clock;
        this.uploadService = uploadService;
        if (viewerHmacSecret == null || viewerHmacSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("zeroverse.upload.viewer-hmac-secret must be at least 32 UTF-8 bytes");
        }
        this.viewerHmacSecret = viewerHmacSecret;
    }

    @Transactional
    public PostDetail create(Long userId, CreatePostRequest request) {
        Blog blog = lockBlogForWrite(request.blogId(), userId);
        Category category = resolveCategory(blog, request.categoryId());
        validateTitle(request.title(), Boolean.TRUE.equals(request.publish()));
        PostDtos.Visibility dtoVisibility = requiredVisibility(request.visibility());
        PostContentService.ValidatedContent content = contentService.validateAndRender(
                request.contentJson(), request.contentHtml(), Boolean.TRUE.equals(request.publish()));
        List<PostImageInput> images = normalizeImages(request.images());
        validateImageSnapshot(content.imageUrls(), images);

        LocalDateTime now = now();
        Post post = Post.create(
                blog.getUser(), blog, category, request.title().trim(), content.json(), content.html(),
                cleanNullable(request.thumbnailUrl()), toEntityVisibility(dtoVisibility),
                Boolean.TRUE.equals(request.publish()), now);
        postRepository.saveAndFlush(post);
        syncImages(post, images);
        uploadService.bindPostImages(userId, post, content.imageUrls(), post.getThumbnailUrl());
        syncTags(post, request.tagNames());
        entityManager.flush();
        return toDetail(post, userId);
    }

    @Transactional
    public PostDetail update(Long userId, Long postId, UpdatePostRequest request) {
        Post current = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001));
        Blog blog = lockBlogForWrite(current.getBlog().getId(), userId);
        Post post = postRepository.findByIdAndDeletedAtIsNullForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001));
        assertPostOwner(post, userId);
        Category category = resolveCategory(blog, request.categoryId());
        validateTitle(request.title(), Boolean.TRUE.equals(request.publish()));
        PostContentService.ValidatedContent content = contentService.validateAndRender(
                request.contentJson(), request.contentHtml(), Boolean.TRUE.equals(request.publish()));
        List<PostImageInput> images = normalizeImages(request.images());
        validateImageSnapshot(content.imageUrls(), images);

        post.update(category, request.title().trim(), content.json(), content.html(),
                cleanNullable(request.thumbnailUrl()), toEntityVisibility(requiredVisibility(request.visibility())),
                Boolean.TRUE.equals(request.publish()), now());
        syncImages(post, images);
        uploadService.bindPostImages(userId, post, content.imageUrls(), post.getThumbnailUrl());
        syncTags(post, request.tagNames());
        entityManager.flush();
        return toDetail(post, userId);
    }

    @Transactional
    public void delete(Long userId, Long postId) {
        Post current = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001));
        lockBlogForWrite(current.getBlog().getId(), userId);
        Post post = postRepository.findByIdAndDeletedAtIsNullForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001));
        assertPostOwner(post, userId);
        for (PostImage image : postImageRepository.findByPostIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(postId)) {
            image.softDelete();
        }
        post.softDelete();
        postRepository.saveAndFlush(post);
    }

    @Transactional
    public PostDetail get(Long postId, Long viewerId, String anonymousKey) {
        Post post = postRepository.findByIdAndDeletedAtIsNullForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001));
        if (!accessPolicy.canRead(post, viewerId)) {
            throw new BusinessException(viewerId == null ? ErrorCode.AUTH_004 : ErrorCode.POST_002);
        }
        recordView(post, viewerId, anonymousKey);
        return toDetail(post, viewerId);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostSummary> listByBlog(
            Long blogId, Long viewerId, int page, int size, String sort,
            Long categoryId, String tag, PostDtos.Visibility visibility, Boolean publish) {
        Blog blog = blogRepository.findByIdAndDeletedAtIsNullAndUserDeletedAtIsNull(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));
        return listCandidates(blog.getId(), viewerId, page, size, sort, categoryId,
                blog.getUser().getId(), normalizeTag(tag), toEntityVisibility(visibility), publish);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostSummary> listBySlug(
            String slug, Long viewerId, int page, int size, String sort,
            Long categoryId, String tag, PostDtos.Visibility visibility, Boolean publish) {
        Blog blog = blogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));
        return listCandidates(blog.getId(), viewerId, page, size, sort, categoryId,
                blog.getUser().getId(), normalizeTag(tag), toEntityVisibility(visibility), publish);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostSummary> listDrafts(Long viewerId, int page, int size) {
        requireViewer(viewerId);
        Pageable pageable = pageRequest(page, size, null, false);
        if (pageOffsetExceedsJpaInteger(page, size)) {
            Page<Post> firstPage = postRepository.findOwnedDrafts(
                    null, viewerId, null, null, null, PageRequest.of(0, size,
                            pageable.getSort()));
            return emptyOutOfRangePage(firstPage, page, size);
        }
        Page<Post> result = postRepository.findOwnedDrafts(
                null, viewerId, null, null, null, pageable);
        return PageResponse.from(result.map(post -> toSummary(post, viewerId)));
    }

    @Transactional(readOnly = true)
    public PageResponse<PostSummary> listByTag(
            String tagName, Long viewerId, int page, int size, String sort, Boolean publish) {
        String normalized = normalizeTag(tagName);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "태그가 필요합니다.");
        }
        return listCandidates(null, viewerId, page, size, sort, null, null, normalized, null, publish);
    }

    @Transactional
    public List<PostImageInput> updateImages(Long userId, Long postId, List<PostImageInput> requested) {
        Post current = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001));
        lockBlogForWrite(current.getBlog().getId(), userId);
        Post post = postRepository.findByIdAndDeletedAtIsNullForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001));
        assertPostOwner(post, userId);
        List<PostImageInput> images = normalizeImages(requested);
        PostContentService.ValidatedContent content = contentService.validateAndRender(
                post.getContentJson(), post.getContentHtml(), false);
        validateImageSnapshot(content.imageUrls(), images);
        syncImages(post, images);
        uploadService.bindPostImages(userId, post, content.imageUrls(), post.getThumbnailUrl());
        entityManager.flush();
        return readImages(postId);
    }

    private PageResponse<PostSummary> listCandidates(
            Long blogId, Long viewerId, int page, int size, String sort, Long categoryId,
            Long blogOwnerId, String tag, Visibility visibility, Boolean publish) {
        if (Boolean.FALSE.equals(publish)) {
            if (viewerId == null) {
                throw new BusinessException(ErrorCode.AUTH_004);
            }
            if (blogOwnerId != null && !Objects.equals(blogOwnerId, viewerId)) {
                // Explicit draft filtering by a non-owner must not disclose whether drafts exist.
                throw new BusinessException(ErrorCode.POST_002);
            }
            Pageable pageable = pageRequest(page, size, sort, false);
            if (pageOffsetExceedsJpaInteger(page, size)) {
                Page<Post> firstPage = postRepository.findOwnedDrafts(
                        blogId, viewerId, categoryId, tag, visibility,
                        PageRequest.of(0, size, pageable.getSort()));
                return emptyOutOfRangePage(firstPage, page, size);
            }
            Page<Post> drafts = postRepository.findOwnedDrafts(
                    blogId, viewerId, categoryId, tag, visibility, pageable);
            return PageResponse.from(drafts.map(post -> toSummary(post, viewerId)));
        }
        Pageable pageable = pageRequest(page, size, sort, true);
        if (pageOffsetExceedsJpaInteger(page, size)) {
            Page<Post> firstPage = postRepository.findVisiblePublished(
                    blogId, viewerId, categoryId, tag, visibility,
                    Visibility.PUBLIC, Visibility.UNIVERSE, UniverseStatus.ACCEPTED,
                    PageRequest.of(0, size, pageable.getSort()));
            return emptyOutOfRangePage(firstPage, page, size);
        }
        Page<Post> published = postRepository.findVisiblePublished(
                blogId, viewerId, categoryId, tag, visibility,
                Visibility.PUBLIC, Visibility.UNIVERSE, UniverseStatus.ACCEPTED,
                pageable);
        return PageResponse.from(published.map(post -> toSummary(post, viewerId)));
    }

    private void recordView(Post post, Long viewerId, String anonymousKey) {
        cleanupExpiredViewRecords(now().minus(VIEW_RECORD_RETENTION));
        String viewerKey = viewerId == null
                ? "a:" + hmac(anonymousKey == null ? "unknown" : anonymousKey)
                : "u:" + viewerId;
        LocalDateTime now = now();
        PostViewRecord existing = postViewRecordRepository
                .findForUpdate(post.getId(), viewerKey).orElse(null);
        if (existing == null) {
            postViewRecordRepository.save(PostViewRecord.create(post, viewerKey, now));
            post.incrementViewCount();
            return;
        }
        if (Duration.between(existing.getLastViewedAt(), now).compareTo(Duration.ofHours(24)) >= 0) {
            existing.viewedAt(now);
            post.incrementViewCount();
        }
    }

    /** 개인정보성 anonymous ledger가 무기한 쌓이지 않도록 요청당 작은 batch만 정리한다. */
    private void cleanupExpiredViewRecords(LocalDateTime cutoff) {
        jdbcTemplate.update("""
                DELETE FROM post_view_records
                 WHERE last_viewed_at < ?
                 ORDER BY last_viewed_at ASC
                 LIMIT ?
                """, cutoff, VIEW_RECORD_CLEANUP_BATCH_SIZE);
    }

    private Blog lockBlogForWrite(Long blogId, Long userId) {
        Blog blog = blogRepository.findByIdAndDeletedAtIsNullForUpdate(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));
        User owner = userRepository.findByIdAndDeletedAtIsNullForUpdate(blog.getUser().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));
        if (!Objects.equals(owner.getId(), userId)) {
            throw new BusinessException(ErrorCode.POST_003);
        }
        if (!owner.isActive()) {
            throw new BusinessException(owner.getStatus() == com.zeroverse.domain.user.entity.UserStatus.SUSPENDED
                    ? ErrorCode.USER_003 : ErrorCode.BLOG_001);
        }
        return blog;
    }

    private Category resolveCategory(Blog blog, Long categoryId) {
        if (categoryId == null) {
            return categoryRepository.findFirstByBlogIdAndTypeAndDeletedAtIsNull(
                            blog.getId(), com.zeroverse.domain.category.entity.CategoryType.DEFAULT)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CAT_001));
        }
        return categoryRepository.findByIdAndBlogIdAndDeletedAtIsNull(categoryId, blog.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CAT_001));
    }

    private void assertPostOwner(Post post, Long userId) {
        if (!accessPolicy.canWrite(post, userId)) {
            if (post.getUser().getStatus() == com.zeroverse.domain.user.entity.UserStatus.SUSPENDED) {
                throw new BusinessException(ErrorCode.USER_003);
            }
            throw new BusinessException(ErrorCode.POST_003);
        }
    }

    private void syncImages(Post post, List<PostImageInput> requested) {
        for (PostImage image : postImageRepository
                .findByPostIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(post.getId())) {
            image.softDelete();
        }
        entityManager.flush();
        for (PostImageInput input : requested) {
            postImageRepository.save(PostImage.create(post, input.imageUrl(), input.altText(), input.displayOrder()));
        }
    }

    private void syncTags(Post post, List<String> names) {
        postTagRepository.deleteAllByPostId(post.getId());
        entityManager.flush();
        for (String normalized : normalizeTags(names)) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO tags (name, normalized_name) VALUES (?, ?)", normalized, normalized);
            Tag tag = tagRepository.findByNormalizedName(normalized)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_500));
            postTagRepository.save(PostTag.create(post, tag));
        }
    }

    private List<PostImageInput> normalizeImages(List<PostImageInput> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        if (images.size() > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "본문 이미지는 100개 이하여야 합니다.");
        }
        Set<Integer> seenOrders = new LinkedHashSet<>();
        List<PostImageInput> ordered = new ArrayList<>();
        for (PostImageInput image : images) {
            if (image == null || image.imageUrl() == null || image.imageUrl().isBlank()
                    || image.displayOrder() == null || image.altText() != null && image.altText().length() > 255) {
                throw new BusinessException(ErrorCode.VALIDATION_001, "본문 이미지가 올바르지 않습니다.");
            }
            if (image.displayOrder() < 0 || !seenOrders.add(image.displayOrder())) {
                throw new BusinessException(ErrorCode.VALIDATION_001,
                        "본문 이미지 displayOrder는 중복 없는 0부터의 연속 값이어야 합니다.");
            }
            ordered.add(new PostImageInput(
                    image.imageUrl(), cleanNullable(image.altText()), image.displayOrder()));
        }
        ordered.sort(Comparator.comparing(PostImageInput::displayOrder));
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).displayOrder() != i) {
                throw new BusinessException(ErrorCode.VALIDATION_001,
                        "본문 이미지 displayOrder는 중복 없는 0부터의 연속 값이어야 합니다.");
            }
        }
        // The JSON image set is a set by contract: repeated URL references are one managed image.
        // Keep the first metadata row and renumber the resulting snapshot contiguously.
        Set<String> seenUrls = new LinkedHashSet<>();
        List<PostImageInput> normalized = new ArrayList<>();
        for (PostImageInput image : ordered) {
            if (seenUrls.add(image.imageUrl())) {
                normalized.add(new PostImageInput(
                        image.imageUrl(), image.altText(), normalized.size()));
            }
        }
        return List.copyOf(normalized);
    }

    private static void validateImageSnapshot(Set<String> bodyUrls, List<PostImageInput> images) {
        Set<String> imageUrls = images.stream().map(PostImageInput::imageUrl).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!bodyUrls.equals(imageUrls)) {
            throw new BusinessException(ErrorCode.UPLOAD_004, "본문 이미지와 이미지 목록이 일치하지 않습니다.");
        }
    }

    private List<PostImageInput> readImages(Long postId) {
        return postImageRepository.findByPostIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(postId).stream()
                .map(image -> new PostImageInput(image.getImageUrl(), image.getAltText(), image.getDisplayOrder()))
                .toList();
    }

    private PostDetail toDetail(Post post, Long viewerId) {
        return new PostDetail(
                post.getId(), post.getBlog().getId(), post.getBlog().getUrlSlug(), post.getBlog().getTitle(),
                new PostAuthor(post.getUser().getId(), post.getUser().getNickname(), post.getUser().getProfileImageUrl()),
                new PostCategory(post.getCategory().getId(), post.getCategory().getName()), post.getTitle(),
                post.getContentJson(), post.getContentHtml(), post.getThumbnailUrl(), toDtoVisibility(post.getVisibility()),
                post.getViewCount(), toPublishedAtUtc(post.getPublishedAt()), toAuditTimeUtc(post.getCreatedAt()),
                toAuditTimeUtc(post.getUpdatedAt()), readTags(post.getId()),
                readImages(post.getId()), previous(post, viewerId), next(post, viewerId));
    }

    private PostSummary toSummary(Post post, Long viewerId) {
        return new PostSummary(
                post.getId(), post.getBlog().getId(), post.getBlog().getUrlSlug(), post.getBlog().getTitle(),
                new PostAuthor(post.getUser().getId(), post.getUser().getNickname(), post.getUser().getProfileImageUrl()),
                new PostCategory(post.getCategory().getId(), post.getCategory().getName()), post.getTitle(),
                excerpt(post.getContentHtml()), post.getThumbnailUrl(), toDtoVisibility(post.getVisibility()),
                post.getViewCount(), toPublishedAtUtc(post.getPublishedAt()), toAuditTimeUtc(post.getCreatedAt()),
                toAuditTimeUtc(post.getUpdatedAt()), readTags(post.getId()));
    }

    private AdjacentPost previous(Post post, Long viewerId) {
        if (post.getPublishedAt() == null) return null;
        return postRepository.findPreviousCandidates(
                        post.getBlog().getId(), post.getId(), post.getPublishedAt(), viewerId,
                        Visibility.PUBLIC, Visibility.UNIVERSE, UniverseStatus.ACCEPTED,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(candidate -> new AdjacentPost(candidate.getId(), candidate.getTitle(), candidate.getBlog().getUrlSlug()))
                .orElse(null);
    }

    private AdjacentPost next(Post post, Long viewerId) {
        if (post.getPublishedAt() == null) return null;
        return postRepository.findNextCandidates(
                        post.getBlog().getId(), post.getId(), post.getPublishedAt(), viewerId,
                        Visibility.PUBLIC, Visibility.UNIVERSE, UniverseStatus.ACCEPTED,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(candidate -> new AdjacentPost(candidate.getId(), candidate.getTitle(), candidate.getBlog().getUrlSlug()))
                .orElse(null);
    }

    private List<String> readTags(Long postId) {
        return postTagRepository.findAllByPostId(postId).stream()
                .map(postTag -> postTag.getTag().getName()).toList();
    }

    private static String excerpt(String html) {
        if (html == null) return "";
        String text = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return text.length() <= 240 ? text : text.substring(0, 240);
    }

    /**
     * PostService's publish clock is UTC, while legacy BaseEntity auditing stores the server's
     * local wall-clock value in a timezone-less DATETIME. Convert both representations to an
     * explicit UTC offset at the HTTP boundary without rewriting existing database rows.
     */
    private static OffsetDateTime toPublishedAtUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private static OffsetDateTime toAuditTimeUtc(LocalDateTime value) {
        return value == null
                ? null
                : value.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
    }

    private static Pageable pageRequest(int page, int size, String sort, boolean publish) {
        validatePage(page, size);
        Sort ordering;
        if (!publish) {
            ordering = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
        } else if ("popular".equalsIgnoreCase(sort)) {
            ordering = Sort.by(
                    Sort.Order.desc("viewCount"),
                    Sort.Order.desc("publishedAt"),
                    Sort.Order.desc("id"));
        } else {
            ordering = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
        }
        return PageRequest.of(page, size, ordering);
    }

    /**
     * Spring Data JPA binds JPQL offsets as an int even though Pageable exposes a long offset.
     * Querying an enormous, known-out-of-range page through that binder would turn a normal
     * empty page into InvalidDataAccessApiUsageException. Fetch page zero only to obtain the
     * database count, then return the requested empty page without narrowing the offset.
     */
    private static boolean pageOffsetExceedsJpaInteger(int page, int size) {
        return (long) page * size > Integer.MAX_VALUE;
    }

    private static <T> PageResponse<T> emptyOutOfRangePage(Page<?> firstPage, int page, int size) {
        long offset = (long) page * size;
        long total = firstPage.getTotalElements();
        if (offset < total) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "페이지 범위가 너무 큽니다.");
        }
        long totalPagesLong = (total + size - 1L) / size;
        int totalPages = (int) Math.min(Integer.MAX_VALUE, totalPagesLong);
        return new PageResponse<>(List.of(), page, size, total, totalPages, false, page > 0);
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "페이지 값이 올바르지 않습니다.");
        }
    }

    private static void validateTitle(String title, boolean publish) {
        if (title == null) throw new BusinessException(ErrorCode.VALIDATION_001);
        String trimmed = title.trim();
        if (trimmed.length() > 200 || publish && trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "발행 제목은 1~200자여야 합니다.");
        }
    }

    private static List<String> normalizeTags(List<String> names) {
        if (names == null || names.isEmpty()) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String name : names) {
            if (name == null) continue;
            String normalized = name.trim().toLowerCase(Locale.ROOT);
            if (normalized.isBlank()) continue;
            if (normalized.length() > MAX_TAG_LENGTH) {
                throw new BusinessException(ErrorCode.VALIDATION_001, "태그는 100자 이하여야 합니다.");
            }
            result.add(normalized);
        }
        if (result.size() > MAX_TAGS) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "태그는 10개 이하여야 합니다.");
        }
        return List.copyOf(result);
    }

    private static String normalizeTag(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.length() > MAX_TAG_LENGTH ? null : normalized;
    }

    private static String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static PostDtos.Visibility toDtoVisibility(Visibility visibility) {
        return PostDtos.Visibility.valueOf(visibility.name());
    }

    private static Visibility toEntityVisibility(PostDtos.Visibility visibility) {
        return visibility == null ? null : Visibility.valueOf(visibility.name());
    }

    private static PostDtos.Visibility requiredVisibility(PostDtos.Visibility visibility) {
        if (visibility == null) throw new BusinessException(ErrorCode.VALIDATION_001);
        return visibility;
    }

    private static void requireViewer(Long viewerId) {
        if (viewerId == null) throw new BusinessException(ErrorCode.AUTH_004);
    }

    /** MySQL DATETIME(0)는 초 단위이므로 API·DB snapshot의 정밀도를 맞춘다. */
    private LocalDateTime now() { return LocalDateTime.now(clock).withNano(0); }

    private String hmac(String raw) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(viewerHmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("viewer key cannot be generated", e);
        }
    }
}
