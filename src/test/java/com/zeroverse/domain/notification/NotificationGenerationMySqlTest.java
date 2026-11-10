package com.zeroverse.domain.notification;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.comment.dto.CommentDtos;
import com.zeroverse.domain.comment.service.CommentService;
import com.zeroverse.domain.like.service.LikeService;
import com.zeroverse.domain.post.dto.PostDtos.*;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.domain.universe.entity.Universe;
import com.zeroverse.domain.universe.repository.UniverseRepository;
import com.zeroverse.domain.universe.service.UniverseService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

class NotificationGenerationMySqlTest extends NotificationGenerationSupport {
    @Test void commentsDeduplicateRepliesAndExcludeSelf() {
        User owner=user("owner"), parent=user("parent"), actor=user("actor");
        PostDetail p=create(owner,blog(owner),Visibility.PUBLIC,true);
        var root=comments.create(parent.getId(),p.id(),new CommentDtos.CreateCommentRequest("root",null));
        comments.create(actor.getId(),p.id(),new CommentDtos.CreateCommentRequest("reply",root.id()));
        assertThat(events()).containsExactly(owner.getId()+":COMMENT",owner.getId()+":COMMENT",parent.getId()+":REPLY");
        var ownRoot=comments.create(owner.getId(),p.id(),new CommentDtos.CreateCommentRequest("own",null));
        comments.create(actor.getId(),p.id(),new CommentDtos.CreateCommentRequest("reply owner",ownRoot.id()));
        comments.create(parent.getId(),p.id(),new CommentDtos.CreateCommentRequest("self reply",root.id()));
        assertThat(events()).containsExactly(owner.getId()+":COMMENT",owner.getId()+":COMMENT",parent.getId()+":REPLY",owner.getId()+":REPLY",owner.getId()+":COMMENT");
        comments.delete(parent.getId(),root.id());
        assertThat(count()).isEqualTo(5);
    }
    @Test void likesAreActualInsertEventsAndHistorySurvivesUnlike() {
        User owner=user("owner"), actor=user("actor"); PostDetail p=create(owner,blog(owner),Visibility.PUBLIC,true);
        likes.like(owner.getId(),p.id()); assertThat(count()).isZero();
        likes.like(actor.getId(),p.id()); likes.like(actor.getId(),p.id()); assertThat(count()).isEqualTo(1);
        likes.unlike(actor.getId(),p.id()); assertThat(count()).isEqualTo(1);
        likes.like(actor.getId(),p.id()); assertThat(events()).containsExactly(owner.getId()+":LIKE",owner.getId()+":LIKE");
    }
    @Test void requestAcceptAndRerequestEmitButRejectedDuplicatesDoNot() {
        User first=user("first"), second=user("second");
        var r=universe.request(first.getId(),second.getId());
        assertThatThrownBy(()->universe.request(first.getId(),second.getId())).isInstanceOf(RuntimeException.class);
        universe.cancel(first.getId(),r.requestId());
        var again=universe.request(first.getId(),second.getId()); universe.accept(second.getId(),again.requestId());
        assertThatThrownBy(()->universe.accept(second.getId(),again.requestId())).isInstanceOf(RuntimeException.class);
        assertThat(events()).containsExactly(second.getId()+":NEIGHBOR",second.getId()+":NEIGHBOR",first.getId()+":NEIGHBOR");
        assertThat(jdbc.queryForList("SELECT message FROM notifications ORDER BY id",String.class)).containsExactly("유니버스 신청을 받았습니다.","유니버스 신청을 받았습니다.","유니버스 신청이 수락되었습니다.");
    }
    @Test void publishingOnlyFalseToEligibleAndIncludesSuspendedLivingFollowers() {
        User receiver=user("receiver"), suspended=user("suspended"), owner=user("owner"), late=user("late"), blocked=user("blocked"), deleted=user("deleted");
        accepted(receiver,owner); accepted(suspended,owner); accepted(blocked,owner); accepted(deleted,owner);
        relations.saveAndFlush(Universe.blocked(owner,blocked));
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?",suspended.getId());
        jdbc.update("UPDATE users SET deleted_at=NOW() WHERE id=?",deleted.getId());
        Blog b=blog(owner); PostDetail p=create(owner,b,Visibility.PUBLIC,false); assertThat(count()).isZero();
        update(owner,p.id(),Visibility.PRIVATE,true); assertThat(count()).isZero();
        update(owner,p.id(),Visibility.PUBLIC,true); assertThat(count()).isEqualTo(2);
        accepted(late,owner); update(owner,p.id(),Visibility.UNIVERSE,true); assertThat(count()).isEqualTo(2);
        update(owner,p.id(),Visibility.PUBLIC,true); assertThat(count()).isEqualTo(2);
        update(owner,p.id(),Visibility.PRIVATE,true); update(owner,p.id(),Visibility.UNIVERSE,true); assertThat(count()).isEqualTo(5);
        update(owner,p.id(),Visibility.PUBLIC,false); update(owner,p.id(),Visibility.PUBLIC,true); assertThat(count()).isEqualTo(8);
        assertThat(jdbc.queryForList("SELECT DISTINCT receiver_user_id FROM notifications ORDER BY receiver_user_id",Long.class)).containsExactly(receiver.getId(),suspended.getId(),late.getId());
    }
    @Test void replyRecipientMustStillReadUniversePostAndFailuresGenerateNothing() {
        User owner=user("acl-owner"), parent=user("acl-parent"), actor=user("acl-actor");
        Blog b=blog(owner); var p=create(owner,b,Visibility.UNIVERSE,true);
        accepted(parent,owner);accepted(actor,owner);
        var root=comments.create(parent.getId(),p.id(),new CommentDtos.CreateCommentRequest("root",null));
        universe.deleteFollowing(parent.getId(),owner.getId());
        var reply=comments.create(actor.getId(),p.id(),new CommentDtos.CreateCommentRequest("reply",root.id()));
        assertThat(events()).containsExactly(owner.getId()+":COMMENT",owner.getId()+":COMMENT");
        assertThat(jdbc.queryForList("SELECT target_id FROM notifications ORDER BY id",Long.class)).containsExactly(root.id(),reply.id());
        assertThat(jdbc.queryForList("SELECT DISTINCT target_type FROM notifications",String.class)).containsExactly("COMMENT");
        universe.block(actor.getId(),owner.getId());
        long before=count();
        assertThatThrownBy(()->comments.create(actor.getId(),p.id(),new CommentDtos.CreateCommentRequest("blocked",null))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->likes.like(actor.getId(),p.id())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->posts.create(owner.getId(),new CreatePostRequest(b.getId(),"",doc(),null,null,Visibility.PUBLIC,true,null,List.of(),List.of()))).isInstanceOf(RuntimeException.class);
        assertThat(count()).isEqualTo(before);
    }

    @Test void postOwnerReplyOnlyNotifiesOtherParentAuthor() {
        var owner=user("owner-replies");var parent=user("parent-receives");
        var p=create(owner,blog(owner),Visibility.PUBLIC,true);
        var root=comments.create(parent.getId(),p.id(),new CommentDtos.CreateCommentRequest("root",null));
        comments.create(owner.getId(),p.id(),new CommentDtos.CreateCommentRequest("owner reply",root.id()));
        assertThat(events()).containsExactly(owner.getId()+":COMMENT",parent.getId()+":REPLY");
    }

}
