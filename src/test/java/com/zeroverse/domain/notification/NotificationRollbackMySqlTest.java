package com.zeroverse.domain.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.zeroverse.domain.comment.dto.CommentDtos;
import com.zeroverse.domain.notification.service.NotificationGenerator;
import com.zeroverse.domain.post.dto.PostDtos.Visibility;
import com.zeroverse.domain.universe.entity.Universe;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

class NotificationRollbackMySqlTest extends NotificationGenerationSupport {
    @SpyBean NotificationGenerator generator;
    @AfterEach void clearFailure() { reset(generator); }
    void failFlush() { doThrow(new IllegalStateException("forced notification failure")).when(generator).flush(); }
    void failBatch() { doAnswer(i->{i.callRealMethod();throw new IllegalStateException("forced late notification failure");}).when(generator).createBatch(anyList()); }
    @Test void postCreateAndLaterBatchFailureRollBackAllRows() {
        var owner=user("rollback-owner"); var b=blog(owner);
        Long last=null;
        for(int n=0;n<101;n++) {var follower=user("follower"+n);accepted(follower,owner);last=follower.getId();}
        var triggerJdbc=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword()));
        triggerJdbc.execute("CREATE TRIGGER m8_late_batch_failure BEFORE INSERT ON notifications FOR EACH ROW BEGIN IF NEW.receiver_user_id = "+last+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'synthetic late batch failure'; END IF; END");
        try {assertThatThrownBy(()->create(owner,b,Visibility.PUBLIC,true)).isInstanceOf(RuntimeException.class);}
        finally {triggerJdbc.execute("DROP TRIGGER m8_late_batch_failure");}
        assertThat(count()).isZero(); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts",Long.class)).isZero();
    }
    @Test void updateFailurePreservesSourceContentPublishAndAudit() {
        var owner=user("rollback-update-owner"); var receiver=user("rollback-update-receiver"); accepted(receiver,owner);
        var p=create(owner,blog(owner),Visibility.PRIVATE,false);
        var before=jdbc.queryForMap("SELECT title,content_json,content_html,content_text,published_at,updated_at FROM posts WHERE id=?",p.id());
        failBatch(); assertThatThrownBy(()->update(owner,p.id(),Visibility.PUBLIC,true)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForMap("SELECT title,content_json,content_html,content_text,published_at,updated_at FROM posts WHERE id=?",p.id())).isEqualTo(before); assertThat(count()).isZero();
    }
    @Test void commentFailureRollsBackCommentAndNotifications() {
        var owner=user("rollback-comment-owner"); var actor=user("rollback-comment-actor"); var p=create(owner,blog(owner),Visibility.PUBLIC,true);
        failBatch(); assertThatThrownBy(()->comments.create(actor.getId(),p.id(),new CommentDtos.CreateCommentRequest("body",null))).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments",Long.class)).isZero(); assertThat(count()).isZero();
    }
    @Test void likeFailureRollsBackLikeAndNotifications() {
        var owner=user("rollback-like-owner"); var actor=user("rollback-like-actor"); var p=create(owner,blog(owner),Visibility.PUBLIC,true);
        failFlush(); assertThatThrownBy(()->likes.like(actor.getId(),p.id())).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_likes",Long.class)).isZero(); assertThat(count()).isZero();
    }
    @Test void requestAndAcceptFailureRollBackRelationshipAndNotifications() {
        var a=user("rollback-request-a"); var b=user("rollback-request-b"); failFlush();
        assertThatThrownBy(()->universe.request(a.getId(),b.getId())).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM universes",Long.class)).isZero(); assertThat(count()).isZero();
        var relation=relations.saveAndFlush(Universe.pending(a,b));
        assertThatThrownBy(()->universe.accept(b.getId(),relation.getId())).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM universes WHERE id=?",String.class,relation.getId())).isEqualTo("PENDING"); assertThat(count()).isZero();
    }
    @Test void replyFailureRollsBackBothRecipientsAndOnlyTheNewReply() {
        var owner=user("reply-rollback-owner");var parent=user("reply-rollback-parent");var actor=user("reply-rollback-actor");
        var p=create(owner,blog(owner),Visibility.PUBLIC,true);
        var root=comments.create(parent.getId(),p.id(),new CommentDtos.CreateCommentRequest("original root",null));
        assertThat(count()).isEqualTo(1);
        failBatch();
        assertThatThrownBy(()->comments.create(actor.getId(),p.id(),new CommentDtos.CreateCommentRequest("rolled back reply",root.id()))).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE parent_id IS NOT NULL",Long.class)).isZero();
        assertThat(events()).containsExactly(owner.getId()+":COMMENT");
    }

}
