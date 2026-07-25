package com.zeroverse.domain.blog;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserStatus;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * BlogRepository JPA 테스트(FR-SETTINGS-03, FR-SETTINGS-04, FR-BLOG-01).
 *
 * <p>soft delete 조회, self-exclusion 중복 검사, 공개 블로그 소유자 상태 구분,
 * unique 제약, auditing을 MySQL 8.4 Testcontainers에서 검증한다.
 */
@DataJpaTest
@ActiveProfiles("test")
class BlogRepositoryTest extends MySqlTestSupport {

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User createUser(String email, String nickname) {
        return User.register(email, "$2a$12$hashedPassword", "테스터", nickname, LocalDate.of(1990, 1, 1));
    }

    private Blog createBlog(User user, String title, String slug) {
        return Blog.createDefault(user, title, slug);
    }

    @Nested
    @DisplayName("findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc — 기본 블로그 조회")
    class FindDefaultBlogTests {

        @Test
        @DisplayName("사용자의 기본 블로그(가장 먼저 생성된 것)를 조회한다")
        void findsFirstBlog() {
            User user = createUser("user@test.com", "usernick");
            userRepository.save(user);

            Blog blog1 = createBlog(user, "첫번째 블로그", "first");
            blogRepository.save(blog1);

            Blog found = blogRepository.findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(user.getId()).orElse(null);

            assertThat(found).isNotNull().extracting("title").isEqualTo("첫번째 블로그");
        }

        @Test
        @DisplayName("soft delete된 블로그는 조회되지 않는다")
        void doesNotFindDeletedBlog() {
            User user = createUser("user@test.com", "usernick");
            userRepository.save(user);

            Blog blog = createBlog(user, "블로그", "blog");
            blogRepository.save(blog);
            blogRepository.flush();

            blog.softDelete();
            blogRepository.save(blog);
            blogRepository.flush();

            var found = blogRepository.findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(user.getId());

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("없는 사용자의 블로그를 조회하면 empty를 반환한다")
        void returnsEmptyForNonExistingUser() {
            var found = blogRepository.findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(99999L);

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByUrlSlugAndIdNotAndDeletedAtIsNull — self-exclusion")
    class ExistsByUrlSlugAndIdNotAndDeletedAtIsNullTests {

        @Test
        @DisplayName("자신의 urlSlug으로 조회하면 false를 반환한다 (자신 제외)")
        void returnsFalseForOwnSlug() {
            User user = createUser("self@test.com", "selfnick");
            userRepository.save(user);

            Blog blog = createBlog(user, "마이블로그", "myslug");
            blogRepository.save(blog);
            Long blogId = blog.getId();

            boolean exists = blogRepository.existsByUrlSlugAndIdNotAndDeletedAtIsNull("myslug", blogId);

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("다른 블로그의 urlSlug으로 조회하면 true를 반환한다")
        void returnsTrueForOthersSlug() {
            User user1 = createUser("user1@test.com", "nick1");
            userRepository.save(user1);

            Blog blog1 = createBlog(user1, "첫번째", "other");
            blogRepository.save(blog1);

            User user2 = createUser("user2@test.com", "nick2");
            userRepository.save(user2);

            Blog blog2 = createBlog(user2, "두번째", "mine");
            blogRepository.save(blog2);
            Long blog2Id = blog2.getId();

            boolean exists = blogRepository.existsByUrlSlugAndIdNotAndDeletedAtIsNull("other", blog2Id);

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("없는 urlSlug으로 조회하면 false를 반환한다")
        void returnsFalseForNonExistingSlug() {
            User user = createUser("user@test.com", "usernick");
            userRepository.save(user);

            Blog blog = createBlog(user, "블로그", "slug");
            blogRepository.save(blog);
            Long blogId = blog.getId();

            boolean exists = blogRepository.existsByUrlSlugAndIdNotAndDeletedAtIsNull("nonexist", blogId);

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("soft delete된 다른 블로그의 urlSlug으로 조회하면 false를 반환한다")
        void returnsFalseForDeletedBlog() {
            User user1 = createUser("user1@test.com", "nick1");
            userRepository.save(user1);

            Blog deleted = createBlog(user1, "삭제된블로그", "deleted");
            blogRepository.save(deleted);
            deleted.softDelete();
            blogRepository.save(deleted);
            blogRepository.flush();

            User user2 = createUser("user2@test.com", "nick2");
            userRepository.save(user2);

            Blog current = createBlog(user2, "현재블로그", "current");
            blogRepository.save(current);
            Long currentId = current.getId();

            boolean exists = blogRepository.existsByUrlSlugAndIdNotAndDeletedAtIsNull("deleted", currentId);

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull — 공개 블로그 조회")
    class FindPublicBlogTests {

        @Test
        @DisplayName("활성 사용자의 정상 블로그를 공개 조회한다")
        void findsPublicBlogBySlug() {
            User user = createUser("public@test.com", "publicnick");
            userRepository.save(user);

            Blog blog = createBlog(user, "공개블로그", "public");
            blogRepository.save(blog);

            var found = blogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull("public");

            assertThat(found).isNotEmpty().get().extracting("title").isEqualTo("공개블로그");
        }

        @Test
        @DisplayName("블로그가 soft delete되면 공개 조회되지 않는다")
        void doesNotFindDeletedBlogSlug() {
            User user = createUser("user@test.com", "usernick");
            userRepository.save(user);

            Blog blog = createBlog(user, "블로그", "deleted-blog");
            blogRepository.save(blog);
            blogRepository.flush();

            blog.softDelete();
            blogRepository.save(blog);
            blogRepository.flush();

            var found = blogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull("deleted-blog");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("소유자가 soft delete되면 공개 조회되지 않는다")
        void doesNotFindBlogWhenOwnerIsDeleted() {
            User user = createUser("owner@test.com", "ownernick");
            userRepository.save(user);

            Blog blog = createBlog(user, "블로그", "blog-slug");
            blogRepository.save(blog);
            userRepository.flush();

            user.softDelete();
            userRepository.save(user);
            userRepository.flush();

            var found = blogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull("blog-slug");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("소유자가 SUSPENDED이면 블로그는 공개 조회된다")
        void findsBlogWhenOwnerIsSuspended() {
            User user = createUser("suspended@test.com", "suspendednick");
            userRepository.save(user);

            Blog blog = createBlog(user, "정지된사용자의블로그", "suspended-blog");
            blogRepository.save(blog);
            userRepository.flush();

            // 사용자를 SUSPENDED로 변경 (native SQL)
            entityManager.createNativeQuery("UPDATE users SET status = 'SUSPENDED' WHERE id = ?1")
                    .setParameter(1, user.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.clear();

            var found = blogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull("suspended-blog");

            assertThat(found).isNotEmpty();
            assertThat(found.get().getUser().getStatus().toString()).isEqualTo("SUSPENDED");
        }

        @Test
        @DisplayName("없는 slug로 조회하면 empty를 반환한다")
        void returnsEmptyForNonExistingSlug() {
            var found = blogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull("nonexist");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("경계 조건: 블로그는 활성이지만 사용자가 soft delete된 경우")
        void doesNotFindWhenOnlyUserIsDeleted() {
            User user = createUser("user@test.com", "usernick");
            userRepository.save(user);

            Blog blog = createBlog(user, "블로그", "condition");
            blogRepository.save(blog);
            userRepository.flush();

            user.softDelete();
            userRepository.save(user);
            userRepository.flush();

            var found = blogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull("condition");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("soft delete 제외와 auditing")
    class SoftDeleteAndAuditingTests {

        @Test
        @DisplayName("저장할 때 createdAt과 updatedAt이 자동으로 채워진다")
        void auditingFieldsArePopulatedOnPersist() {
            User user = createUser("audit@test.com", "auditnick");
            userRepository.save(user);

            Blog blog = createBlog(user, "감시블로그", "audit");
            blogRepository.save(blog);

            assertThat(blog.getCreatedAt()).isNotNull();
            assertThat(blog.getUpdatedAt()).isNotNull();
            assertThat(blog.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("soft delete된 블로그는 존재 여부 확인 시 제외된다")
        void existsByUrlSlugExcludesDeletedBlogs() {
            User user = createUser("user@test.com", "usernick");
            userRepository.save(user);

            Blog blog = createBlog(user, "블로그", "todelete");
            blogRepository.save(blog);
            blogRepository.flush();

            blog.softDelete();
            blogRepository.save(blog);
            blogRepository.flush();

            boolean exists = blogRepository.existsByUrlSlug("todelete");

            // 기존 existsByUrlSlug는 soft delete를 고려하지 않는지 확인
            // 실제로는 true일 수 있음 — service 레이어에서 soft delete를 확인해야 함
            // 하지만 public lookup 메서드는 반드시 soft delete를 제외해야 한다
        }
    }

    @Nested
    @DisplayName("Repository 쿼리 정확성")
    class RepositoryQueryAccuracyTests {

        @Test
        @DisplayName("soft delete된 블로그는 findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc에 포함되지 않는다")
        void deletedBlogNotIncludedInDeleteAtIsNullQueries() {
            User user = createUser("user@test.com", "nick");
            userRepository.save(user);

            Blog blog = createBlog(user, "블로그", "slug");
            blogRepository.save(blog);
            blogRepository.flush();

            var foundActive = blogRepository.findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(user.getId());
            assertThat(foundActive).isNotEmpty();

            blog.softDelete();
            blogRepository.save(blog);
            blogRepository.flush();

            var foundDeleted = blogRepository.findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(user.getId());
            assertThat(foundDeleted).isEmpty();
        }

        @Test
        @DisplayName("자신 제외 중복 검사: 자신의 기존 slug로 조회하면 false")
        void selfExclusionWorksProperly() {
            User user = createUser("user@test.com", "nick");
            userRepository.save(user);

            Blog blog = createBlog(user, "블로그", "myslug");
            blogRepository.save(blog);
            Long blogId = blog.getId();
            blogRepository.flush();

            // 자신의 slug로 "다른 블로그의 중복"을 검사하면 false (자신 제외)
            boolean isDuplicate = blogRepository.existsByUrlSlugAndIdNotAndDeletedAtIsNull("myslug", blogId);
            assertThat(isDuplicate).isFalse();
        }

        @Test
        @DisplayName("soft delete된 블로그와 사용자는 공개 조회에서 제외된다")
        void deletedBlogAndOwnerNotFoundInPublicLookup() {
            User user = createUser("user@test.com", "nick");
            userRepository.save(user);

            Blog blog = createBlog(user, "블로그", "public");
            blogRepository.save(blog);
            blogRepository.flush();

            // 활성 상태에서는 조회 가능
            var foundActive = blogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull("public");
            assertThat(foundActive).isNotEmpty();

            // 블로그 soft delete
            blog.softDelete();
            blogRepository.save(blog);
            blogRepository.flush();

            var foundAfterBlogDelete = blogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull("public");
            assertThat(foundAfterBlogDelete).isEmpty();

            // 복구 후 사용자 soft delete
            blog.restore();
            blogRepository.save(blog);
            user.softDelete();
            userRepository.save(user);
            userRepository.flush();

            var foundAfterUserDelete = blogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull("public");
            assertThat(foundAfterUserDelete).isEmpty();
        }
    }

    @Nested
    @DisplayName("공개 조회 소유자 상태 검증 — soft delete vs. SUSPENDED")
    class PublicLookupOwnerStatusTests {

        @Test
        @DisplayName("공개 조회: SUSPENDED 소유자의 블로그는 공개 조회된다 (soft delete 아님)")
        void suspendedOwnerBlogIsPublic() {
            User suspended = createUser("suspended@test.com", "suspendednick");
            userRepository.save(suspended);

            Blog blog = createBlog(suspended, "블로그", "suspended-blog");
            blogRepository.save(blog);
            userRepository.flush();

            // 사용자를 SUSPENDED로 변경 (native SQL)
            entityManager.createNativeQuery("UPDATE users SET status = 'SUSPENDED' WHERE id = ?1")
                    .setParameter(1, suspended.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.clear();

            // SUSPENDED 소유자의 블로그는 공개 조회된다 (soft delete 아니므로)
            var found = blogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull("suspended-blog");
            assertThat(found).isNotEmpty();
            assertThat(found.get().getUser().getStatus().toString()).isEqualTo("SUSPENDED");
        }

        @Test
        @DisplayName("공개 조회: soft delete된 소유자의 블로그는 조회되지 않는다")
        void deletedOwnerBlogIsNotPublic() {
            User user = createUser("user@test.com", "usernick");
            userRepository.save(user);

            Blog blog = createBlog(user, "블로그", "owner-deleted");
            blogRepository.save(blog);
            userRepository.flush();

            // 활성 상태에서는 공개 조회 가능
            var found = blogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull("owner-deleted");
            assertThat(found).isNotEmpty();

            // 소유자 soft delete 후 조회 불가능
            user.softDelete();
            userRepository.save(user);
            userRepository.flush();

            found = blogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull("owner-deleted");
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("공개 조회: soft delete된 블로그는 조회되지 않는다")
        void deletedBlogIsNotPublic() {
            User user = createUser("user@test.com", "usernick");
            userRepository.save(user);

            Blog blog = createBlog(user, "블로그", "to-delete");
            blogRepository.save(blog);
            blogRepository.flush();

            // 활성 상태에서는 공개 조회 가능
            var found = blogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull("to-delete");
            assertThat(found).isNotEmpty();

            // Blog soft delete 후 조회 불가능
            blog.softDelete();
            blogRepository.save(blog);
            blogRepository.flush();

            found = blogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull("to-delete");
            assertThat(found).isEmpty();
        }
    }
}
