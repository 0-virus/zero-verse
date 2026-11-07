package com.zeroverse.domain.universe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.universe.dto.UniverseDtos.Availability;
import com.zeroverse.domain.universe.dto.UniverseDtos.RelationshipStatus;
import com.zeroverse.domain.universe.dto.UniverseDtos.UniverseRelationshipSnapshot;
import com.zeroverse.domain.universe.entity.Universe;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import com.zeroverse.domain.universe.repository.UniverseRepository;
import com.zeroverse.domain.universe.service.UniverseService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserStatus;
import com.zeroverse.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UniverseServiceTest {

    private final UniverseRepository universeRepository = mock(UniverseRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final BlogRepository blogRepository = mock(BlogRepository.class);
    private final User actor = mock(User.class);
    private final User target = mock(User.class);
    private final UniverseService service = new UniverseService(
            universeRepository, userRepository, blogRepository);

    @BeforeEach
    void setUp() {
        when(actor.getId()).thenReturn(1L);
        when(actor.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(actor.getNickname()).thenReturn("actor");
        when(target.getId()).thenReturn(2L);
        when(target.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(target.getNickname()).thenReturn("target");
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(actor));
        when(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(target));
        when(blogRepository.findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(2L))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("같은 방향 PENDING 신청은 UNI_002다")
    void duplicateRequestIsRejected() {
        stubPairLocks();
        Universe existing = relation(actor, target, 11L, UniverseStatus.PENDING);
        when(universeRepository.findPair(1L, 2L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.request(1L, 2L))
                .isInstanceOfSatisfying(com.zeroverse.common.exception.BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(
                                com.zeroverse.common.exception.ErrorCode.UNI_002));
    }

    @Test
    @DisplayName("양방향 중 하나라도 BLOCKED면 신청은 UNI_003이다")
    void blockedRequestTakesPriorityOverDuplicateCheck() {
        stubPairLocks();
        Universe existing = relation(target, actor, 12L, UniverseStatus.BLOCKED);
        when(universeRepository.findPair(1L, 2L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.request(1L, 2L))
                .isInstanceOfSatisfying(com.zeroverse.common.exception.BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(
                                com.zeroverse.common.exception.ErrorCode.UNI_003));
    }

    @Test
    @DisplayName("정지된 actor의 신청은 USER_003이다")
    void suspendedActorCannotRequest() {
        stubPairLocks();
        when(actor.getStatus()).thenReturn(UserStatus.SUSPENDED);

        assertThatThrownBy(() -> service.request(1L, 2L))
                .isInstanceOfSatisfying(com.zeroverse.common.exception.BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(
                                com.zeroverse.common.exception.ErrorCode.USER_003));
    }

    @Test
    @DisplayName("신청 수락은 수신자 외 caller에게 UNI_004를 반환한다")
    void acceptHidesWrongOwner() {
        User recipient = mockUser(3L, "recipient");
        Universe request = relation(actor, recipient, 21L, UniverseStatus.PENDING);
        when(universeRepository.findVisibleById(21L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.accept(1L, 21L))
                .isInstanceOfSatisfying(com.zeroverse.common.exception.BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(
                                com.zeroverse.common.exception.ErrorCode.UNI_004));
    }

    @Test
    @DisplayName("이미 수락된 신청의 재수락은 UNI_005다")
    void acceptRejectsInvalidState() {
        Universe request = relation(actor, target, 22L, UniverseStatus.PENDING);
        Universe accepted = relation(actor, target, 22L, UniverseStatus.ACCEPTED);
        when(universeRepository.findVisibleById(22L)).thenReturn(Optional.of(request));
        when(universeRepository.findPair(1L, 2L)).thenReturn(List.of(accepted));
        stubPairLocks();

        assertThatThrownBy(() -> service.accept(2L, 22L))
                .isInstanceOfSatisfying(com.zeroverse.common.exception.BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(
                                com.zeroverse.common.exception.ErrorCode.UNI_005));
    }

    @Test
    @DisplayName("수락된 관계 삭제는 pair lock 후 양방향 재조회 결과를 사용한다")
    void deleteFollowingRereadsPairAfterLock() {
        stubPairLocks();
        Universe accepted = relation(actor, target, 24L, UniverseStatus.ACCEPTED);
        when(universeRepository.findPair(1L, 2L)).thenReturn(List.of(accepted));

        service.deleteFollowing(1L, 2L);

        verify(universeRepository).delete(accepted);
        verify(universeRepository).flush();
    }

    @Test
    @DisplayName("lock 후 정확한 방향 row가 사라지면 관계 삭제는 UNI_004다")
    void deleteFollowingMapsMissingDirectionToNotFound() {
        stubPairLocks();
        Universe opposite = relation(target, actor, 28L, UniverseStatus.ACCEPTED);
        when(universeRepository.findPair(1L, 2L)).thenReturn(List.of(opposite));

        assertThatThrownBy(() -> service.deleteFollowing(1L, 2L))
                .isInstanceOfSatisfying(com.zeroverse.common.exception.BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(
                                com.zeroverse.common.exception.ErrorCode.UNI_004));
    }

    @Test
    @DisplayName("거절은 초기 request 발견 후 pair lock 재조회 결과를 사용한다")
    void rejectRereadsPairAfterLock() {
        Universe initial = relation(actor, target, 25L, UniverseStatus.PENDING);
        when(universeRepository.findVisibleById(25L)).thenReturn(Optional.of(initial));
        stubPairLocks();
        when(universeRepository.findPair(1L, 2L)).thenReturn(List.of(initial));

        service.reject(2L, 25L);

        verify(universeRepository).delete(initial);
        verify(universeRepository).flush();
    }

    @Test
    @DisplayName("취소는 초기 request 발견 후 pair lock 재조회 결과를 사용한다")
    void cancelRereadsPairAfterLock() {
        Universe initial = relation(actor, target, 26L, UniverseStatus.PENDING);
        when(universeRepository.findVisibleById(26L)).thenReturn(Optional.of(initial));
        stubPairLocks();
        when(universeRepository.findPair(1L, 2L)).thenReturn(List.of(initial));

        service.cancel(1L, 26L);

        verify(universeRepository).delete(initial);
        verify(universeRepository).flush();
    }

    @Test
    @DisplayName("차단 해제는 pair lock 후 actor 방향 row를 재조회한다")
    void unblockRereadsPairAfterLock() {
        stubPairLocks();
        Universe blocked = relation(actor, target, 27L, UniverseStatus.BLOCKED);
        when(universeRepository.findPair(1L, 2L)).thenReturn(List.of(blocked));

        service.unblock(1L, 2L);

        verify(universeRepository).delete(blocked);
        verify(universeRepository).flush();
    }

    @Test
    @DisplayName("거절과 취소는 request 소유자가 아니면 UNI_004다")
    void rejectAndCancelHideWrongOwner() {
        Universe request = relation(actor, target, 23L, UniverseStatus.PENDING);
        when(universeRepository.findVisibleById(23L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.reject(3L, 23L))
                .isInstanceOfSatisfying(com.zeroverse.common.exception.BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(
                                com.zeroverse.common.exception.ErrorCode.UNI_004));
        assertThatThrownBy(() -> service.cancel(3L, 23L))
                .isInstanceOfSatisfying(com.zeroverse.common.exception.BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(
                                com.zeroverse.common.exception.ErrorCode.UNI_004));
    }

    @Test
    @DisplayName("차단은 양방향 비차단 관계를 삭제하고 actor 차단을 만든다")
    void blockDeletesBothNonBlockedDirections() {
        stubPairLocks();
        Universe outgoing = relation(actor, target, 31L, UniverseStatus.PENDING);
        Universe incoming = relation(target, actor, 32L, UniverseStatus.ACCEPTED);
        when(universeRepository.findPair(1L, 2L)).thenReturn(List.of(outgoing, incoming));

        service.block(1L, 2L);

        verify(universeRepository).delete(outgoing);
        verify(universeRepository).delete(incoming);
        verify(universeRepository).flush();
        verify(universeRepository).saveAndFlush(any(Universe.class));
    }

    @Test
    @DisplayName("차단은 상대 방향 BLOCKED를 보존한다")
    void blockPreservesOppositeBlock() {
        stubPairLocks();
        Universe outgoing = relation(actor, target, 33L, UniverseStatus.PENDING);
        Universe oppositeBlock = relation(target, actor, 34L, UniverseStatus.BLOCKED);
        when(universeRepository.findPair(1L, 2L)).thenReturn(List.of(outgoing, oppositeBlock));

        service.block(1L, 2L);

        verify(universeRepository).delete(outgoing);
        verify(universeRepository, never()).delete(oppositeBlock);
    }

    @Test
    @DisplayName("snapshot은 방향별 PENDING requestId와 ACCEPTED를 보존한다")
    void snapshotKeepsDirectionalRequestIds() {
        Universe outgoing = relation(actor, target, 41L, UniverseStatus.PENDING);
        Universe incoming = relation(target, actor, 42L, UniverseStatus.ACCEPTED);
        stubRelations(outgoing, incoming);

        UniverseRelationshipSnapshot snapshot = service.snapshot(1L, 2L);

        assertThat(snapshot.availability()).isEqualTo(Availability.AVAILABLE);
        assertThat(snapshot.target().id()).isEqualTo(2L);
        assertThat(snapshot.target().nickname()).isEqualTo("target");
        assertThat(snapshot.outgoing().status()).isEqualTo(RelationshipStatus.PENDING);
        assertThat(snapshot.outgoing().requestId()).isEqualTo(41L);
        assertThat(snapshot.incoming().status()).isEqualTo(RelationshipStatus.ACCEPTED);
        assertThat(snapshot.incoming().requestId()).isNull();
    }

    @Test
    @DisplayName("받은 신청 목록은 counterpart blog slug를 batch query 한 번으로 매핑한다")
    void receivedListUsesBatchBlogLookup() {
        Universe received = relation(target, actor, 51L, UniverseStatus.PENDING);
        when(universeRepository.findReceived(eq(1L), eq(UniverseStatus.PENDING), eq("target"),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(received)));
        Blog targetBlog = mock(Blog.class);
        when(targetBlog.getUser()).thenReturn(target);
        when(targetBlog.getUrlSlug()).thenReturn("target-blog");
        when(blogRepository.findByUserIdInAndDeletedAtIsNullOrdered(List.of(2L)))
                .thenReturn(List.of(targetBlog));

        var page = service.listReceived(1L, "target", 0, 20);

        assertThat(page.items()).singleElement().extracting(item -> item.user().blogSlug())
                .isEqualTo("target-blog");
        verify(blogRepository).findByUserIdInAndDeletedAtIsNullOrdered(List.of(2L));
        verify(blogRepository, never()).findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(2L);
    }

    @Test
    @DisplayName("보낸 신청 목록은 counterpart blog slug를 batch query 한 번으로 매핑한다")
    void sentListUsesBatchBlogLookup() {
        Universe sent = relation(actor, target, 52L, UniverseStatus.PENDING);
        when(universeRepository.findSent(eq(1L), eq(UniverseStatus.PENDING), eq("target"),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sent)));
        Blog targetBlog = mock(Blog.class);
        when(targetBlog.getUser()).thenReturn(target);
        when(targetBlog.getUrlSlug()).thenReturn("target-blog");
        when(blogRepository.findByUserIdInAndDeletedAtIsNullOrdered(List.of(2L)))
                .thenReturn(List.of(targetBlog));

        var page = service.listSent(1L, "target", 0, 20);

        assertThat(page.items()).singleElement().extracting(item -> item.user().blogSlug())
                .isEqualTo("target-blog");
        verify(blogRepository).findByUserIdInAndDeletedAtIsNullOrdered(List.of(2L));
        verify(blogRepository, never()).findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(2L);
    }

    @Test
    @DisplayName("actor가 target을 차단하면 snapshot은 BLOCKED_BY_ME다")
    void outgoingBlockedIsUnblockableByActor() {
        stubRelations(blockedRelation(), null);

        assertThat(service.snapshot(1L, 2L).availability()).isEqualTo(Availability.BLOCKED_BY_ME);
    }

    @Test
    @DisplayName("target이 actor를 차단하면 snapshot은 UNAVAILABLE로 일반화한다")
    void incomingBlockedIsUnavailable() {
        stubRelations(null, blockedRelation());

        assertThat(service.snapshot(1L, 2L).availability()).isEqualTo(Availability.UNAVAILABLE);
    }

    @Test
    @DisplayName("양방향 차단이어도 actor 자신의 차단 해제를 위해 BLOCKED_BY_ME를 우선한다")
    void bothBlockedPrioritizesOutgoingBlock() {
        stubRelations(blockedRelation(), blockedRelation());

        assertThat(service.snapshot(1L, 2L).availability()).isEqualTo(Availability.BLOCKED_BY_ME);
    }

    private void stubRelations(Universe outgoing, Universe incoming) {
        when(universeRepository.findByFromUserIdAndToUserId(1L, 2L))
                .thenReturn(Optional.ofNullable(outgoing));
        when(universeRepository.findByFromUserIdAndToUserId(2L, 1L))
                .thenReturn(Optional.ofNullable(incoming));
    }

    private void stubPairLocks() {
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).thenReturn(Optional.of(actor));
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(2L)).thenReturn(Optional.of(target));
    }

    private Universe relation(User from, User to, Long id, UniverseStatus status) {
        Universe relation = mock(Universe.class);
        when(relation.getId()).thenReturn(id);
        when(relation.getFromUser()).thenReturn(from);
        when(relation.getToUser()).thenReturn(to);
        when(relation.getStatus()).thenReturn(status);
        return relation;
    }

    private User mockUser(Long id, String nickname) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getNickname()).thenReturn(nickname);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        return user;
    }

    private Universe blockedRelation() {
        Universe relation = mock(Universe.class);
        when(relation.getStatus()).thenReturn(UniverseStatus.BLOCKED);
        return relation;
    }
}
