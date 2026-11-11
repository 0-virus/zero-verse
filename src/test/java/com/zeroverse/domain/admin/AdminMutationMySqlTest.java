package com.zeroverse.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.admin.service.AdminService;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.post.repository.PostRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.entity.UserStatus;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real MySQL mutation effects, current-actor errors, audit and child preservation. */
@SpringBootTest
@ActiveProfiles("test")
class AdminMutationMySqlTest extends MySqlTestSupport {
    @Autowired AdminService admin;
    @Autowired UserRepository users;
    @Autowired BlogRepository blogs;
    @Autowired PostRepository posts;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void roleAndStatusReturnCanonicalDetailAndChangeOnlyTheRequestedField() {
        User actor = admin("mutation-admin");
        User target = user("mutation-target");
        LocalDateTime old = LocalDateTime.of(2020, 1, 1, 0, 0);
        jdbc.update("UPDATE users SET updated_at = ? WHERE id = ?", old, target.getId());
        var changed = admin.changeRole(actor.getId(), target.getId(), UserRole.ADMIN);
        assertThat(changed.role()).isEqualTo(UserRole.ADMIN);
        assertThat(changed.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(changed.updatedAt().toLocalDateTime()).isAfter(old);
        assertThat(changed.email()).isEqualTo(target.getEmail());
        assertThat(changed.lastAccessAt()).isNull();
        var suspended = admin.changeStatus(actor.getId(), target.getId(), UserStatus.SUSPENDED);
        assertThat(suspended.status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(suspended.role()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.changeStatus(actor.getId(), target.getId(), UserStatus.ACTIVE).status())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void sameValueIncludingSelfPreservesEveryAuditAndUserColumn() {
        User actor = admin("idempotent-admin");
        User target = user("idempotent-target");
        List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM users ORDER BY id");
        admin.changeRole(actor.getId(), actor.getId(), UserRole.ADMIN);
        admin.changeStatus(actor.getId(), actor.getId(), UserStatus.ACTIVE);
        admin.changeRole(actor.getId(), target.getId(), UserRole.USER);
        admin.changeStatus(actor.getId(), target.getId(), UserStatus.ACTIVE);
        assertThat(jdbc.queryForList("SELECT * FROM users ORDER BY id")).isEqualTo(before);
    }

    @Test
    void selfRevocationSuspensionAndDeletionHaveNoSideEffects() {
        User actor = admin("self-protected-admin");
        List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM users");
        expect(ErrorCode.ADMIN_002, () -> admin.changeRole(actor.getId(), actor.getId(), UserRole.USER));
        expect(ErrorCode.ADMIN_002, () -> admin.changeStatus(actor.getId(), actor.getId(), UserStatus.SUSPENDED));
        expect(ErrorCode.ADMIN_002, () -> admin.delete(actor.getId(), actor.getId()));
        assertThat(jdbc.queryForList("SELECT * FROM users")).isEqualTo(before);
    }

    @Test
    void missingAndDeletedTargetsAre404IncludingRepeatedDelete() {
        User actor = admin("missing-admin");
        User target = user("missing-target");
        for (long id : new long[] {999999L, target.getId()}) {
            if (id == target.getId()) admin.delete(actor.getId(), id);
            expect(ErrorCode.USER_001, () -> admin.changeRole(actor.getId(), id, UserRole.ADMIN));
            expect(ErrorCode.USER_001, () -> admin.changeStatus(actor.getId(), id, UserStatus.ACTIVE));
            expect(ErrorCode.USER_001, () -> admin.delete(actor.getId(), id));
        }
    }

    @Test
    void currentActorErrorsTakePrecedenceOverMissingTargetForEveryMutation() {
        User actor = admin("actor-errors");
        for (String state : new String[] {"USER", "SUSPENDED", "DELETED", "MISSING"}) {
            jdbc.update("UPDATE users SET role='ADMIN',status='ACTIVE',deleted_at=NULL WHERE id=?", actor.getId());
            ErrorCode error;
            if (state.equals("USER")) {
                jdbc.update("UPDATE users SET role='USER' WHERE id=?", actor.getId());
                error = ErrorCode.ADMIN_001;
            } else if (state.equals("SUSPENDED")) {
                jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", actor.getId());
                error = ErrorCode.USER_003;
            } else {
                jdbc.update("UPDATE users SET deleted_at=CURRENT_TIMESTAMP WHERE id=?", actor.getId());
                error = ErrorCode.USER_001;
            }
            long actorId = state.equals("MISSING") ? 987654L : actor.getId();
            expect(error, () -> admin.changeRole(actorId, 999999L, UserRole.ADMIN));
            expect(error, () -> admin.changeStatus(actorId, 999999L, UserStatus.SUSPENDED));
            expect(error, () -> admin.delete(actorId, 999999L));
            expect(error, () -> admin.list(actorId, null, null, null, 0, 20));
            expect(error, () -> admin.get(actorId, 999999L));
        }
    }

    @Test
    void managedActorBeforeLockCannotAuthorizeWithStaleRole() {
        User actor = admin("managed-admin");
        User target = user("managed-target");
        expect(ErrorCode.ADMIN_001, () -> new TransactionTemplate(transactionManager).execute(status -> {
            assertThat(entityManager.find(User.class, actor.getId()).getRole()).isEqualTo(UserRole.ADMIN);
            jdbc.update("UPDATE users SET role='USER' WHERE id=?", actor.getId());
            return admin.changeRole(actor.getId(), target.getId(), UserRole.ADMIN);
        }));
        assertThat(users.findById(target.getId()).orElseThrow().getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void deleteChangesOnlyUserDeletedAtAndUpdatedAtAndPreservesAllChildRows() {
        User actor = admin("preserve-admin");
        User target = user("preserve-target");
        Blog blog = blogs.saveAndFlush(Blog.createDefault(target, "Preserved", "preserve"));
        Post post = posts.saveAndFlush(Post.create(target, blog, null, "Preserved",
                mapper.createObjectNode().put("type", "doc"), "<p>preserved</p>", null,
                Visibility.PUBLIC, true, LocalDateTime.now()));
        jdbc.update("INSERT INTO categories(blog_id,name,type,display_order) VALUES (?,'Saved','GENERAL',0)", blog.getId());
        jdbc.update("INSERT INTO comments(post_id,user_id,content) VALUES (?,?,'saved')", post.getId(), target.getId());
        jdbc.update("INSERT INTO post_likes(post_id,user_id) VALUES (?,?)", post.getId(), target.getId());
        jdbc.update("INSERT INTO universes(from_user_id,to_user_id,status) VALUES (?,?,'ACCEPTED')", actor.getId(), target.getId());
        jdbc.update("INSERT INTO notifications(receiver_user_id,actor_user_id,type,target_type,target_id,message) VALUES (?,?,'LIKE','POST',?,'saved')", actor.getId(), target.getId(), post.getId());
        jdbc.update("INSERT INTO refresh_tokens(user_id,token_id,token_hash,expires_at) VALUES (?,'preserved-jti','preserved-hash','2099-01-01')", target.getId());
        jdbc.update("INSERT INTO image_uploads(id,owner_user_id,purpose,storage_key,image_url,content_type,file_size) VALUES ('00000000-0000-0000-0000-000000000001',?,'POST_IMAGE','saved.png','/saved.png','image/png',10)", target.getId());
        jdbc.update("INSERT INTO post_images(post_id,image_url,display_order) VALUES (?,'/saved.png',0)", post.getId());
        Map<String, List<Map<String, Object>>> before = childRows();
        Map<String, Object> userBefore = jdbc.queryForMap("SELECT * FROM users WHERE id=?", target.getId());
        admin.delete(actor.getId(), target.getId());
        Map<String, Object> userAfter = jdbc.queryForMap("SELECT * FROM users WHERE id=?", target.getId());
        assertThat(userAfter.get("deleted_at")).isNotNull();
        userBefore.remove("deleted_at"); userBefore.remove("updated_at");
        userAfter.remove("deleted_at"); userAfter.remove("updated_at");
        assertThat(userAfter).isEqualTo(userBefore);
        assertThat(childRows()).isEqualTo(before);
    }

    private Map<String, List<Map<String, Object>>> childRows() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : List.of("blogs", "categories", "posts", "comments", "post_likes",
                "universes", "notifications", "image_uploads", "post_images", "refresh_tokens")) {
            result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        }
        return result;
    }

    private User admin(String key) {
        User user = user(key);
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", user.getId());
        return user;
    }

    private User user(String key) {
        return users.saveAndFlush(User.register(key + "@test.local", "hash", "Admin fixture", key,
                LocalDate.of(1990, 1, 1)));
    }

    private static void expect(ErrorCode error, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(error));
    }
}
