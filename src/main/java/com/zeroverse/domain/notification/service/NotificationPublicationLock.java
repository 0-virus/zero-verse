package com.zeroverse.domain.notification.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Scalar blog → globally sorted user rows → relation-only current recheck. */
@Component
public class NotificationPublicationLock {
    private final BlogRepository blogs;
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    public NotificationPublicationLock(BlogRepository blogs, UserRepository users, JdbcTemplate jdbc) {
        this.blogs = blogs;
        this.users = users;
        this.jdbc = jdbc;
    }

    public LockedPublication lock(Long blogId, Long actorId) {
        var target = blogs.findLockTargetByIdForUpdate(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));
        if (!Objects.equals(target.getOwnerId(), actorId)) {
            throw new BusinessException(ErrorCode.POST_003);
        }
        Set<Long> candidateIds = new TreeSet<>(snapshotCandidates(actorId));
        candidateIds.add(actorId);
        Map<Long, User> locked = new LinkedHashMap<>();
        for (Long id : candidateIds) {
            // A receiver may have been deleted since the scalar snapshot. Never fail the post
            // for that disappearance, and never let a stale managed User revive the receiver.
            users.findByIdAndDeletedAtIsNullForUpdate(id).ifPresent(user -> locked.put(id, user));
        }
        User owner = locked.get(actorId);
        if (owner == null) {
            throw new BusinessException(ErrorCode.BLOG_001);
        }
        if (!owner.isActive()) {
            throw new BusinessException(ErrorCode.USER_003);
        }

        Blog blog = blogs.findByIdAndDeletedAtIsNull(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));
        return new LockedPublication(blog, Set.copyOf(candidateIds), Map.copyOf(locked));
    }

    /** Called after the existing post lock (update), immediately before changing the source. */
    public List<User> confirmReceivers(LockedPublication publication) {
        Long actorId = publication.blog().getUser().getId();
        Set<Long> current = currentCandidates(actorId);
        for (Long id : current) {
            // Scalar non-locking lookup only distinguishes a deleted, newly discovered candidate.
            // A live new ID causes a full rollback; it is NEVER locked out of sorted order here.
            if (!publication.candidateIds().contains(id) && users.findStatusTargetById(id).isPresent()) {
                throw new NotificationTransactionExecutor.CandidateChangedException();
            }
        }
        return current.stream()
                .filter(id -> !id.equals(actorId))
                .map(publication.users()::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<Long> snapshotCandidates(Long authorId) {
        return jdbc.queryForList("""
                SELECT u.from_user_id FROM universes u JOIN users r ON r.id = u.from_user_id
                WHERE u.to_user_id = ? AND u.status = 'ACCEPTED' AND r.deleted_at IS NULL
                  AND u.from_user_id <> ? AND NOT EXISTS (SELECT 1 FROM universes b
                    WHERE b.status = 'BLOCKED' AND ((b.from_user_id = u.from_user_id AND b.to_user_id = ?)
                      OR (b.from_user_id = ? AND b.to_user_id = u.from_user_id)))
                ORDER BY u.from_user_id
                """, Long.class, authorId, authorId, authorId, authorId);
    }

    public Set<Long> currentCandidates(Long authorId) {
        // No JOIN/subquery on users: FOR UPDATE cannot acquire an unseen user lock out of order.
        // All relationship writers must hold the author row, so this set stays stable until commit.
        List<Relation> rows = jdbc.query("""
                SELECT from_user_id, to_user_id, status FROM universes
                WHERE from_user_id = ? OR to_user_id = ? FOR UPDATE
                """, (rs, row) -> new Relation(rs.getLong(1), rs.getLong(2), rs.getString(3)), authorId, authorId);
        Set<Long> candidates = new TreeSet<>();
        Set<Long> blocked = new HashSet<>();
        for (Relation relation : rows) {
            if (relation.to().equals(authorId) && relation.status().equals("ACCEPTED")) {
                candidates.add(relation.from());
            }
            if (relation.status().equals("BLOCKED")) {
                blocked.add(relation.from().equals(authorId) ? relation.to() : relation.from());
            }
        }
        candidates.removeAll(blocked);
        candidates.remove(authorId);
        return candidates;
    }

    private record Relation(Long from, Long to, String status) {}

    public record LockedPublication(Blog blog, Set<Long> candidateIds, Map<Long, User> users) {}
}
