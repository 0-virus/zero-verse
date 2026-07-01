<!-- Parent: ../../../AGENTS.md -->

# com.zeroverse Backend Implementation Guide

Base package: `com.zeroverse`

## Package Structure

```
com.zeroverse/
├─ ZeroverseServerApplication.java    // @SpringBootApplication
├─ common/                             // Cross-cutting concerns
│  ├─ response/                        // ApiResponse, PageResponse, ErrorResponse
│  ├─ exception/                       // ErrorCode, BusinessException, GlobalExceptionHandler
│  ├─ entity/                          // BaseEntity, BaseSoftDeleteEntity
│  └─ util/                            // Slug generator, sanitizer, normalization
├─ config/                             // Spring configuration
│  ├─ SecurityConfig.java
│  ├─ SecurityAuthenticationEntryPoint.java
│  ├─ SecurityAccessDeniedHandler.java
│  ├─ CorsConfig.java
│  ├─ OpenApiConfig.java
│  ├─ QuerydslConfig.java
│  ├─ S3Properties.java
│  └─ JpaAuditingConfig.java
├─ security/                           // JWT & authentication (M1+)
│  ├─ jwt/
│  │  ├─ JwtProvider.java              // Token generation/validation
│  │  └─ JwtAuthenticationFilter.java  // OncePerRequestFilter
│  ├─ CustomUserDetails.java
│  └─ @CurrentUser annotation
└─ domain/                             // Business domain (organized by entity)
   ├─ auth/                            // FR-AUTH (M1)
   │  ├─ controller/
   │  ├─ service/
   │  ├─ dto/
   │  ├─ repository/
   │  └─ entity/ (RefreshToken)
   ├─ user/                            // FR-SETTINGS (M2)
   │  ├─ controller/
   │  ├─ service/
   │  ├─ dto/
   │  ├─ repository/
   │  └─ entity/ (User, no repository in M0)
   ├─ blog/                            // FR-SETTINGS, FR-BLOG (M2)
   │  ├─ controller/
   │  ├─ service/
   │  ├─ dto/
   │  ├─ repository/
   │  └─ entity/ (Blog)
   ├─ category/                        // FR-CAT (M3)
   ├─ post/                            // FR-POST (M4)
   │  ├─ controller/
   │  ├─ service/
   │  ├─ dto/
   │  ├─ repository/
   │  └─ entity/ (Post, PostImage, Tag, PostTag)
   ├─ universe/                        // FR-UNI (M5)
   ├─ comment/                         // FR-COM (M6)
   ├─ like/                            // FR-LIKE (M6)
   ├─ feed/                            // FR-FEED (M7)
   │  ├─ service/ (query only)
   │  └─ dto/
   ├─ search/                          // FR-SEARCH (M7)
   │  ├─ service/
   │  └─ dto/
   ├─ notification/                    // FR-NOT (M8)
   ├─ upload/                          // FR-UPLOAD (M4)
   │  ├─ controller/
   │  ├─ service/
   │  └─ dto/
   └─ admin/                           // FR-ADMIN (M9)
      ├─ controller/
      ├─ service/
      └─ dto/
```

## Naming Conventions

### Database (snake_case)
- Table: `users`, `blogs`, `categories`, `posts`, `comments`, `post_likes`, `tags`, etc.
- Column: `user_id`, `created_at`, `updated_at`, `deleted_at`, `blog_id`, `parent_id`, `normalized_name`
- Index: `idx_blog_id`, `idx_email`, `uk_post_user_like`

### Java Code (camelCase)
- Class: `User`, `Blog`, `Post`, `Category`, `RefreshToken`
- Field: `userId`, `createdAt`, `updatedAt`, `deletedAt`, `blogId`, `parentId`, `normalizedName`
- Method: `getUserId()`, `setCreatedAt()`, `isDeleted()`
- Package: `com.zeroverse.domain.user`, `com.zeroverse.domain.blog`

### Wrapper Types (JPA)
Required for nullable columns to distinguish null from 0:
- `Long` (not `long`) for `id`, `user_id`, `blog_id`, etc.
- `Integer` (not `int`) for `view_count`, `display_order`
- `LocalDateTime` (not primitives) for `created_at`, `updated_at`, `deleted_at`
- `String` for all text fields

**Example:**
```java
@Entity
@Table(name = "users")
public class User extends BaseSoftDeleteEntity {
    @Column(name = "email", nullable = false, unique = true)
    private String email;
    
    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;
}
```

## Entity Inheritance

### BaseEntity (for all entities)
Provides `id`, `createdAt`, `updatedAt` with Auditing.

### BaseSoftDeleteEntity (for user-facing data)
Extends BaseEntity, adds `deletedAt` for soft delete.

Soft delete targets: `User`, `Blog`, `Post`, `Category`, `Comment`, `PostImage`, `Notification`

**Hard delete (no soft delete):** `Universe`, `Like`, `PostTag`, `Tag`, `RefreshToken`

## Domain Package Rules

1. **One entity per domain folder** (e.g., `user/entity/User.java`, `blog/entity/Blog.java`)
2. **Related entities grouped** (e.g., `post/entity/` contains Post, PostImage, Tag, PostTag)
3. **Layer structure in each domain:**
   - `controller/` — REST endpoints, request/response handling, validation errors → 400
   - `service/` — Business logic, transaction boundaries, auth checks → 401/403
   - `dto/` — Request/Response DTOs, query projections
   - `repository/` — JPA Repository, custom query methods (QueryDSL in separate custom repos)
   - `entity/` — JPA entities (only M0 creates base entities; domain entities appear M1+)

4. **No cross-domain coupling** in services (use facade/orchestration service if needed)

## Common Patterns

### Error Handling
```java
// Throw BusinessException with ErrorCode
throw new BusinessException(ErrorCode.USER_001);
```

### Response Building
```java
// Success
return ApiResponse.success(data);

// Error
return ApiResponse.error(ErrorResponse.of(code, message));
```

### Pagination
```java
PageRequest pageable = PageRequest.of(page, size, Sort.by("id").descending());
Page<T> result = repository.findAll(pageable);
return PageResponse.of(result);
```

### Soft Delete Filtering
```java
// Use custom repository query to exclude deleted_at IS NOT NULL
@Query("SELECT u FROM User u WHERE u.deletedAt IS NULL")
List<User> findAllActive();
```

### QueryDSL Custom Repository
```java
// Domain service with QueryDSL for complex queries
@Service
public class PostQueryService {
    private final JPAQueryFactory queryFactory;
    
    public List<Post> searchPosts(String keyword) {
        QPost post = QPost.post;
        return queryFactory.selectFrom(post)
            .where(post.title.contains(keyword).and(post.deletedAt.isNull()))
            .fetch();
    }
}
```

## M0 Scope (Current)

✓ Base structure only:
- Package layout
- Common response/exception
- Base entities (BaseEntity, BaseSoftDeleteEntity)
- Configuration (Security, CORS, OpenAPI, QueryDSL, S3)
- DB migration schema (V1__init.sql)
- Test framework setup

✗ **No domain entities or repositories** (ddl-auto=validate, schema only)

Domain implementation starts M1 (auth) and proceeds through M2-M9.

## References

- Main AGENTS.md: `../../../../AGENTS.md`
- Requirements: `../../../../docs/REQUIREMENTS.md` (§4 domain, §NFR-08 constraints)
- PRD: `../../../../docs/PRD.md` (§3 domain model, §10 milestones)
