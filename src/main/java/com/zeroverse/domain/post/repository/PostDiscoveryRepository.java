package com.zeroverse.domain.post.repository;

import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.zeroverse.domain.blog.entity.QBlog;
import com.zeroverse.domain.category.entity.QCategory;
import com.zeroverse.domain.feed.dto.FeedDtos.Relation;
import com.zeroverse.domain.feed.dto.FeedDtos.Sort;
import com.zeroverse.domain.post.entity.QPost;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.search.dto.SearchDtos.Type;
import com.zeroverse.domain.tag.entity.QPostTag;
import com.zeroverse.domain.tag.entity.QTag;
import com.zeroverse.domain.universe.entity.QUniverse;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import com.zeroverse.domain.user.entity.QUser;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 피드·검색의 ACL과 검색 조건을 DB에서 먼저 적용하는 QueryDSL repository. */
@Repository
public class PostDiscoveryRepository {

    private static final int PROJECTION_TEXT_LENGTH = 240;

    private final JPAQueryFactory queryFactory;

    public PostDiscoveryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    /** 목록에 필요한 scalar만 읽는다. content_json/content_html은 projection에 포함하지 않는다. */
    public record PostRow(
            Long id,
            Long blogId,
            String blogSlug,
            String blogTitle,
            Long authorId,
            String authorNickname,
            String authorProfileImageUrl,
            Long categoryId,
            String categoryName,
            String title,
            String contentText,
            String thumbnailUrl,
            Visibility visibility,
            Integer viewCount,
            LocalDateTime publishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Integer postMatch,
            Integer blogMatch,
            Integer userMatch,
            Integer tagMatch) {}

    public long countFeed(Long viewerId, Relation relation, String keyword, boolean publicFeed) {
        QPost post = QPost.post;
        QBlog blog = QBlog.blog;
        QUser user = QUser.user;
        BooleanExpression predicate = basePredicate(post, blog, user)
                .and(post.publishedAt.isNotNull());
        if (publicFeed) {
            predicate = predicate.and(post.visibility.eq(Visibility.PUBLIC));
        } else {
            predicate = predicate.and(universeFeedPredicate(post, user, viewerId, relation));
        }
        predicate = predicate.and(feedKeywordPredicate(post, user, keyword));
        Long count = queryFactory.select(post.id.countDistinct())
                .from(post)
                .join(post.blog, blog)
                .join(post.user, user)
                .where(predicate)
                .fetchOne();
        return count == null ? 0L : count;
    }

    public List<PostRow> findFeed(
            Long viewerId, Relation relation, String keyword, boolean publicFeed,
            long offset, int size, Sort sort) {
        QPost post = QPost.post;
        QBlog blog = QBlog.blog;
        QUser user = QUser.user;
        QCategory category = QCategory.category;
        BooleanExpression predicate = basePredicate(post, blog, user)
                .and(post.publishedAt.isNotNull());
        if (publicFeed) {
            predicate = predicate.and(post.visibility.eq(Visibility.PUBLIC));
        } else {
            predicate = predicate.and(universeFeedPredicate(post, user, viewerId, relation));
        }
        predicate = predicate.and(feedKeywordPredicate(post, user, keyword));

        var query = queryFactory.select(rowProjection(post, blog, user, category,
                        Expressions.constant(0), Expressions.constant(0),
                        Expressions.constant(0), Expressions.constant(0)))
                .from(post)
                .join(post.blog, blog)
                .join(post.user, user)
                .leftJoin(post.category, category)
                .where(predicate)
                .orderBy(feedOrder(post, sort))
                .offset(offset)
                .limit(size);
        return query.fetch();
    }

    public long countSearch(Long viewerId, String keyword, Type type) {
        QPost post = QPost.post;
        QBlog blog = QBlog.blog;
        QUser user = QUser.user;
        BooleanExpression predicate = searchBasePredicate(post, blog, user, viewerId)
                .and(searchMatchPredicate(post, blog, user, keyword, type));
        Long count = queryFactory.select(post.id.countDistinct())
                .from(post)
                .join(post.blog, blog)
                .join(post.user, user)
                .where(predicate)
                .fetchOne();
        return count == null ? 0L : count;
    }

    public List<PostRow> findSearch(
            Long viewerId, String keyword, Type type, long offset, int size,
            com.zeroverse.domain.search.dto.SearchDtos.Sort sort) {
        QPost post = QPost.post;
        QBlog blog = QBlog.blog;
        QUser user = QUser.user;
        QCategory category = QCategory.category;
        BooleanExpression predicate = searchBasePredicate(post, blog, user, viewerId)
                .and(searchMatchPredicate(post, blog, user, keyword, type));

        BooleanExpression postMatch = searchMatchPredicate(post, blog, user, keyword, Type.POST);
        BooleanExpression blogMatch = searchMatchPredicate(post, blog, user, keyword, Type.BLOG);
        BooleanExpression userMatch = searchMatchPredicate(post, blog, user, keyword, Type.USER);
        BooleanExpression tagMatch = searchMatchPredicate(post, blog, user, keyword, Type.TAG);

        var query = queryFactory.select(rowProjection(post, blog, user, category,
                        flag(postMatch), flag(blogMatch), flag(userMatch), flag(tagMatch)))
                .from(post)
                .join(post.blog, blog)
                .join(post.user, user)
                .leftJoin(post.category, category)
                .where(predicate)
                .orderBy(searchOrder(post, sort))
                .offset(offset)
                .limit(size);
        return query.fetch();
    }

    private ConstructorExpression<PostRow> rowProjection(
            QPost post, QBlog blog, QUser user, QCategory category,
            Expression<Integer> postMatch, Expression<Integer> blogMatch,
            Expression<Integer> userMatch, Expression<Integer> tagMatch) {
        Expression<String> contentExcerpt = Expressions.stringTemplate(
                "substring({0}, 1, {1})", post.contentText, PROJECTION_TEXT_LENGTH);
        return com.querydsl.core.types.Projections.constructor(PostRow.class,
                post.id,
                blog.id,
                blog.urlSlug,
                blog.title,
                user.id,
                user.nickname,
                user.profileImageUrl,
                category.id,
                category.name,
                post.title,
                contentExcerpt,
                post.thumbnailUrl,
                post.visibility,
                post.viewCount,
                post.publishedAt,
                post.createdAt,
                post.updatedAt,
                postMatch,
                blogMatch,
                userMatch,
                tagMatch);
    }

    private BooleanExpression basePredicate(QPost post, QBlog blog, QUser user) {
        return post.deletedAt.isNull()
                .and(blog.deletedAt.isNull())
                .and(user.deletedAt.isNull());
    }

    private BooleanExpression searchBasePredicate(
            QPost post, QBlog blog, QUser user, Long viewerId) {
        BooleanExpression own = viewerId == null
                ? Expressions.FALSE
                : user.id.eq(viewerId);
        BooleanExpression publicPost = post.publishedAt.isNotNull()
                .and(post.visibility.eq(Visibility.PUBLIC));
        BooleanExpression universePost = post.publishedAt.isNotNull()
                .and(post.visibility.eq(Visibility.UNIVERSE))
                .and(acceptedRelation(viewerId, user));
        return basePredicate(post, blog, user).and(own.or(publicPost).or(universePost));
    }

    private BooleanExpression universeFeedPredicate(
            QPost post, QUser user, Long viewerId, Relation relation) {
        if (viewerId == null) {
            return Expressions.FALSE;
        }
        BooleanExpression outgoing = acceptedRelation(viewerId, user);
        BooleanExpression incoming = acceptedRelation(user.id, viewerId);
        BooleanExpression relationPredicate = switch (relation == null ? Relation.ALL : relation) {
            case ALL -> outgoing;
            case FRIENDS -> outgoing.and(incoming);
            case FOLLOWING -> outgoing.and(incoming.not());
        };
        return user.id.ne(viewerId)
                .and(post.visibility.eq(Visibility.PUBLIC)
                        .or(post.visibility.eq(Visibility.UNIVERSE)))
                .and(relationPredicate);
    }

    private BooleanExpression acceptedRelation(Long viewerId, QUser targetUser) {
        if (viewerId == null) {
            return Expressions.FALSE;
        }
        QUniverse universe = new QUniverse("acceptedRelation");
        return JPAExpressions.selectOne()
                .from(universe)
                .where(universe.fromUser.id.eq(viewerId),
                        universe.toUser.id.eq(targetUser.id),
                        universe.status.eq(UniverseStatus.ACCEPTED))
                .exists();
    }

    private BooleanExpression acceptedRelation(com.querydsl.core.types.dsl.NumberPath<Long> targetUserId, Long viewerId) {
        if (viewerId == null) {
            return Expressions.FALSE;
        }
        QUniverse universe = new QUniverse("acceptedIncomingRelation");
        return JPAExpressions.selectOne()
                .from(universe)
                .where(universe.fromUser.id.eq(targetUserId),
                        universe.toUser.id.eq(viewerId),
                        universe.status.eq(UniverseStatus.ACCEPTED))
                .exists();
    }

    private BooleanExpression feedKeywordPredicate(QPost post, QUser user, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Expressions.TRUE;
        }
        return contains(post.title, keyword).or(contains(user.nickname, keyword));
    }

    private BooleanExpression searchMatchPredicate(
            QPost post, QBlog blog, QUser user, String keyword, Type type) {
        BooleanExpression postMatch = contains(post.title, keyword)
                .or(contains(post.contentText, keyword));
        BooleanExpression blogMatch = contains(blog.title, keyword);
        BooleanExpression userMatch = contains(user.nickname, keyword);
        BooleanExpression tagMatch = tagExists(post, keyword);
        return switch (type == null ? Type.ALL : type) {
            case ALL -> postMatch.or(blogMatch).or(userMatch).or(tagMatch);
            case POST -> postMatch;
            case BLOG -> blogMatch;
            case USER -> userMatch;
            case TAG -> tagMatch;
        };
    }

    private BooleanExpression tagExists(QPost post, String keyword) {
        QPostTag postTag = new QPostTag("searchPostTag");
        QTag tag = new QTag("searchTag");
        return JPAExpressions.selectOne()
                .from(postTag)
                .join(postTag.tag, tag)
                .where(postTag.post.eq(post), contains(tag.name, keyword))
                .exists();
    }

    private BooleanExpression contains(StringExpression expression, String keyword) {
        String normalized = keyword.toLowerCase(java.util.Locale.ROOT);
        String escaped = normalized
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return expression.lower().like("%" + escaped + "%", '\\');
    }

    private NumberExpression<Integer> flag(BooleanExpression predicate) {
        return new CaseBuilder().when(predicate).then(1).otherwise(0);
    }

    private com.querydsl.core.types.OrderSpecifier<?>[] feedOrder(QPost post, Sort sort) {
        if (sort == Sort.POPULAR) {
            return new com.querydsl.core.types.OrderSpecifier<?>[] {
                post.viewCount.desc(), post.publishedAt.desc(), post.id.desc()
            };
        }
        return new com.querydsl.core.types.OrderSpecifier<?>[] {
            post.publishedAt.desc(), post.id.desc()
        };
    }

    private com.querydsl.core.types.OrderSpecifier<?>[] searchOrder(
            QPost post, com.zeroverse.domain.search.dto.SearchDtos.Sort sort) {
        var effective = sort == null
                ? com.zeroverse.domain.search.dto.SearchDtos.Sort.LATEST : sort;
        var publishedOrCreated = post.publishedAt.coalesce(post.createdAt);
        if (effective == com.zeroverse.domain.search.dto.SearchDtos.Sort.POPULAR) {
            return new com.querydsl.core.types.OrderSpecifier<?>[] {
                post.viewCount.desc(), publishedOrCreated.desc(), post.id.desc()
            };
        }
        return new com.querydsl.core.types.OrderSpecifier<?>[] {
            publishedOrCreated.desc(), post.id.desc()
        };
    }
}
