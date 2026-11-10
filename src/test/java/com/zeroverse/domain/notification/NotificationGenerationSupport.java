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

@SpringBootTest
@ActiveProfiles("test")
abstract class NotificationGenerationSupport extends MySqlTestSupport {
    @Autowired UserRepository users;
    @Autowired BlogRepository blogs;
    @Autowired CategoryRepository categories;
    @Autowired UniverseRepository relations;
    @Autowired UniverseService universe;
    @Autowired PostService posts;
    @Autowired CommentService comments;
    @Autowired LikeService likes;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    User user(String key) { return users.saveAndFlush(User.register(key+"@m8.test", "hash", key, key, LocalDate.of(1990,1,1))); }
    Blog blog(User owner) { Blog b=blogs.saveAndFlush(Blog.createDefault(owner,"blog", "m8-"+owner.getId())); categories.saveAndFlush(Category.createDefault(b)); return b; }
    com.fasterxml.jackson.databind.JsonNode doc() { return mapper.createObjectNode().put("type","doc").set("content",mapper.createArrayNode().add(mapper.createObjectNode().put("type","paragraph").set("content",mapper.createArrayNode().add(mapper.createObjectNode().put("type","text").put("text","safe body"))))); }
    PostDetail create(User owner, Blog b, Visibility v, boolean publish) { return posts.create(owner.getId(),new CreatePostRequest(b.getId(),"title",doc(),null,null,v,publish,null,List.of(),List.of())); }
    void update(User owner, long id, Visibility v, boolean publish) { posts.update(owner.getId(), id,new UpdatePostRequest("edited",doc(),null,null,v,publish,null,List.of(),List.of())); }
    long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM notifications",Long.class); }
    void accepted(User from, User to) { Universe u=Universe.pending(from,to); u.accept(); relations.saveAndFlush(u); }
    List<String> events() { return jdbc.query("SELECT CONCAT(receiver_user_id, ':', type) FROM notifications ORDER BY id",(rs,n)->rs.getString(1)); }

}
