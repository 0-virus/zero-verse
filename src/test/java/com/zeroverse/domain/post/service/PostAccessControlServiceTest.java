package com.zeroverse.domain.post.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PostAccessControlService 테스트")
class PostAccessControlServiceTest {

    private PostAccessControlService service;

    @BeforeEach
    void setUp() {
        service = new PostAccessControlService();
    }

    @Test
    @DisplayName("PUBLIC 발행 게시글은 누구나 접근 가능")
    void testPublicPublishedAccessible() {
        Post post = createMockPost(1L, Visibility.PUBLIC, true);

        // 작성자, 다른 사용자, 비로그인 모두 접근 가능
        service.canView(post, 1L);
        service.canView(post, 2L);
        service.canView(post, null);
    }

    @Test
    @DisplayName("PUBLIC 임시저장은 작성자만 접근 가능")
    void testPublicDraftAccessible() {
        Post post = createMockPost(1L, Visibility.PUBLIC, false);

        // 작성자 접근 가능
        service.canView(post, 1L);

        // 다른 사용자는 404
        assertThatThrownBy(() -> service.canView(post, 2L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.POST_001);
    }

    @Test
    @DisplayName("PRIVATE는 작성자만 접근 가능")
    void testPrivateAccessible() {
        Post post = createMockPost(1L, Visibility.PRIVATE, true);

        // 작성자 접근 가능
        service.canView(post, 1L);

        // 다른 사용자는 403
        assertThatThrownBy(() -> service.canView(post, 2L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.POST_002);
    }

    @Test
    @DisplayName("UNIVERSE 발행은 로그인 사용자 접근 가능 (M4a 임시)")
    void testUniversePublishedAccessible() {
        Post post = createMockPost(1L, Visibility.UNIVERSE, true);

        // 로그인 사용자 접근 가능
        service.canView(post, 2L);

        // 비로그인은 401
        assertThatThrownBy(() -> service.canView(post, null))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.AUTH_004);
    }

    @Test
    @DisplayName("UNIVERSE 임시저장은 작성자만 접근 가능")
    void testUniverseDraftAccessible() {
        Post post = createMockPost(1L, Visibility.UNIVERSE, false);

        // 작성자 접근 가능
        service.canView(post, 1L);

        // 다른 사용자는 404
        assertThatThrownBy(() -> service.canView(post, 2L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.POST_001);
    }

    @Test
    @DisplayName("canModify는 작성자만 허용")
    void testCanModifyAuthorOnly() {
        Post post = createMockPost(1L, Visibility.PUBLIC, true);

        // 작성자는 수정 가능
        service.canModify(post, 1L);

        // 다른 사용자는 403
        assertThatThrownBy(() -> service.canModify(post, 2L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.POST_003);
    }

    private Post createMockPost(Long userId, Visibility visibility, boolean published) {
        Post post = Mockito.mock(Post.class);

        User user = Mockito.mock(User.class);
        Mockito.when(user.getId()).thenReturn(userId);

        Mockito.when(post.isOwner(Mockito.anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return userId != null && userId.equals(id);
        });
        Mockito.when(post.isPublished()).thenReturn(published);
        Mockito.when(post.getVisibility()).thenReturn(visibility);

        return post;
    }
}
