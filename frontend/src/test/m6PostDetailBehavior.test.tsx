import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { PostDetailPage } from '../pages/PostDetailPage';
import { CommentSection } from '../features/comment/CommentSection';
import { ApiRequestError } from '../lib/apiClient';
import type { AuthUser } from '../types/auth';
import type { PageResponse, PostDetail } from '../features/post/types';
import type { CommentItem } from '../features/comment/types';
import type { InteractionState } from '../features/like/types';

const postApi = vi.hoisted(() => ({
  getPost: vi.fn(),
  deletePost: vi.fn(),
}));
const commentApi = vi.hoisted(() => ({
  listComments: vi.fn(),
  createComment: vi.fn(),
  updateComment: vi.fn(),
  deleteComment: vi.fn(),
}));
const likeApi = vi.hoisted(() => ({
  getInteractions: vi.fn(),
  likePost: vi.fn(),
  unlikePost: vi.fn(),
}));
const auth = vi.hoisted(() => ({ useOptionalAuth: vi.fn() }));

vi.mock('../features/post/postApi', () => postApi);
vi.mock('../features/comment/commentApi', () => commentApi);
vi.mock('../features/like/likeApi', () => likeApi);
vi.mock('../lib/authContext', () => auth);

const viewer: AuthUser = {
  id: 7,
  email: 'viewer@example.com',
  name: '별사람',
  nickname: '별사람',
  role: 'USER',
  profileImageUrl: null,
  defaultBlog: { id: 2, title: '궤도역학', urlSlug: 'orbit', isSetupCompleted: true },
};

const post: PostDetail = {
  id: 4,
  blogId: 2,
  blogSlug: 'orbit',
  blogTitle: '궤도역학',
  author: { id: 9, nickname: 'owner', profileImageUrl: null },
  category: { id: 3, name: '개발' },
  title: '첫 로그',
  contentJson: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: '본문' }] }] },
  contentHtml: '<p>본문</p>',
  thumbnailUrl: null,
  visibility: 'PUBLIC',
  viewCount: 12,
  publishedAt: '2026-11-09T00:00:00Z',
  createdAt: '2026-11-09T00:00:00Z',
  updatedAt: '2026-11-09T00:00:00Z',
  tags: ['react'],
  images: [],
  previous: null,
  next: null,
};

function page<T>(items: T[], overrides: Partial<PageResponse<T>> = {}): PageResponse<T> {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length > 0 ? 1 : 0,
    hasNext: false,
    hasPrevious: false,
    ...overrides,
  };
}

function comment(overrides: Partial<CommentItem> = {}): CommentItem {
  return {
    id: 11,
    postId: 4,
    parentId: null,
    author: { id: 7, nickname: '별사람', profileImageUrl: null },
    content: '따뜻한 신호',
    deleted: false,
    createdAt: '2026-11-09T00:00:00Z',
    updatedAt: '2026-11-09T00:00:00Z',
    canEdit: true,
    canDelete: true,
    canReply: true,
    replies: page([], { totalElements: 0, totalPages: 0 }),
    ...overrides,
  };
}

const interactions: InteractionState = {
  postId: 4,
  commentCount: 2,
  likeCount: 8,
  liked: false,
  canComment: true,
  canLike: true,
  canUnlike: false,
};

function accessError(code: string, status: number): ApiRequestError {
  return new ApiRequestError(status, { code, message: '접근할 수 없습니다.', details: [] });
}

function renderDetail(initialEntries = ['/blog/orbit/4']) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <Routes>
        <Route path="/blog/:blogSlug/:postId" element={<PostDetailPage />} />
        <Route path="/blog/:blogSlug" element={<p>블로그 목록</p>} />
        <Route path="/edit/:postId" element={<p>편집 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  auth.useOptionalAuth.mockReturnValue({ user: viewer, isLoading: false, isAuthenticated: true });
  postApi.getPost.mockResolvedValue(post);
  postApi.deletePost.mockResolvedValue(null);
  commentApi.listComments.mockResolvedValue(page([comment()]));
  commentApi.createComment.mockResolvedValue(comment({ id: 12, content: '새 신호' }));
  commentApi.updateComment.mockResolvedValue(comment({ content: '수정된 신호' }));
  commentApi.deleteComment.mockResolvedValue(null);
  likeApi.getInteractions.mockResolvedValue(interactions);
  likeApi.likePost.mockResolvedValue({ ...interactions, liked: true, likeCount: 9, canLike: false, canUnlike: true });
  likeApi.unlikePost.mockResolvedValue(interactions);
  vi.stubGlobal('confirm', vi.fn(() => true));
});

afterEach(() => {
  vi.resetAllMocks();
  vi.unstubAllGlobals();
});

describe('M6 PostDetailPage 댓글·좋아요 행동', () => {
  it('canonical interaction으로 좋아요 수와 토글 상태를 표시한다', async () => {
    const user = userEvent.setup();
    renderDetail();

    const like = await screen.findByRole('button', { name: /좋아요 8/ });
    expect(like).toHaveAttribute('aria-pressed', 'false');
    await user.click(like);

    expect(like).toHaveTextContent('좋아요 9');
    await waitFor(() => expect(likeApi.likePost).toHaveBeenCalledWith(4));
    await waitFor(() => expect(like).toHaveAttribute('aria-pressed', 'true'));
  });

  it('좋아요 실패 시 optimistic count를 rollback하고 오류를 알린다', async () => {
    const user = userEvent.setup();
    likeApi.likePost.mockImplementationOnce(() => new Promise((_, reject) => {
      window.setTimeout(() => reject(new Error('network')), 20);
    }));
    renderDetail();

    const like = await screen.findByRole('button', { name: /좋아요 8/ });
    await waitFor(() => expect(like).not.toBeDisabled());
    await user.click(like);
    expect(like).toHaveTextContent('좋아요 9');

    await waitFor(() => expect(like).toHaveTextContent('좋아요 8'));
    expect(await screen.findByRole('alert')).toHaveTextContent('좋아요를 처리하지 못했습니다.');
  });

  it('기존 좋아요는 canUnlike 계약으로 optimistic 취소 후 canonical 상태를 반영한다', async () => {
    const user = userEvent.setup();
    const liked = { ...interactions, liked: true, likeCount: 9, canLike: false, canUnlike: true };
    likeApi.getInteractions.mockResolvedValueOnce(liked);
    likeApi.unlikePost.mockResolvedValueOnce(interactions);
    renderDetail();

    const like = await screen.findByRole('button', { name: /좋아요 9/ });
    await waitFor(() => expect(like).not.toBeDisabled());
    await user.click(like);
    expect(like).toHaveTextContent('좋아요 8');
    await waitFor(() => expect(likeApi.unlikePost).toHaveBeenCalledWith(4));
    await waitFor(() => expect(like).toHaveAttribute('aria-pressed', 'false'));
  });

  it('삭제 placeholder와 1단계 답글을 같은 댓글 카드에 표시한다', async () => {
    const deleted = comment({
      id: 10,
      author: null,
      content: null,
      deleted: true,
      canEdit: false,
      canDelete: false,
      canReply: false,
    });
    const reply = comment({ id: 12, parentId: 10, content: '남겨진 답글', canReply: false });
    commentApi.listComments.mockResolvedValueOnce(page([{
      ...deleted,
      replies: page([reply]),
    }]));
    renderDetail();

    expect(await screen.findByText('삭제된 댓글')).toBeInTheDocument();
    expect(screen.getByText('탈퇴한 사용자')).toBeInTheDocument();
    expect(screen.getByText('남겨진 답글')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '삭제된 댓글 수정' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '댓글 10 답글' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '댓글 10 삭제' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '댓글 12 답글' })).not.toBeInTheDocument();
  });

  it('root page와 reply page를 독립적으로 요청한다', async () => {
    const user = userEvent.setup();
    const root = comment({ replies: page([comment({ id: 12, parentId: 11, canReply: false })], {
      totalElements: 21,
      totalPages: 2,
      hasNext: true,
    }) });
    const replyPage = page([comment({ id: 13, parentId: 11, content: '두 번째 답글', canReply: false })], {
      page: 1,
      totalElements: 21,
      totalPages: 2,
      hasNext: false,
      hasPrevious: true,
    });
    commentApi.listComments.mockResolvedValueOnce(page([root])).mockResolvedValueOnce(replyPage);
    renderDetail();

    expect(await screen.findAllByText('따뜻한 신호')).not.toHaveLength(0);
    await user.click(screen.getByRole('button', { name: '댓글 11 답글 다음 페이지' }));

    await waitFor(() => expect(commentApi.listComments).toHaveBeenCalledWith(4, { parentId: 11, page: 1, size: 20 }));
    expect(await screen.findByText('두 번째 답글')).toBeInTheDocument();
    expect(commentApi.listComments).toHaveBeenCalledWith(4, { page: 0, size: 20 });
  });

  it('늦은 좋아요 mutation 응답은 계정 전환 뒤 새 화면을 덮지 않는다', async () => {
    const deferredLike = new Promise<InteractionState>(() => undefined);
    likeApi.likePost.mockReturnValueOnce(deferredLike);
    const { rerender } = renderDetail();
    const like = await screen.findByRole('button', { name: /좋아요 8/ });
    const user = userEvent.setup();
    await user.click(like);

    auth.useOptionalAuth.mockReturnValue({ user: { ...viewer, id: 8, nickname: '새 계정' }, isLoading: false, isAuthenticated: true });
    rerender(
      <MemoryRouter initialEntries={['/blog/orbit/4']}>
        <Routes><Route path="/blog/:blogSlug/:postId" element={<PostDetailPage />} /></Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(likeApi.getInteractions).toHaveBeenCalledTimes(2));
    const nextLike = await screen.findByRole('button', { name: /좋아요 8/ });
    await waitFor(() => expect(nextLike).not.toBeDisabled());
    await user.click(nextLike);
    await waitFor(() => expect(likeApi.likePost).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(nextLike).toHaveAttribute('aria-pressed', 'true'));
  });

  it('계정 전환 뒤 늦은 LIKE_001 거부는 새 좋아요 상태와 mutation을 덮지 않는다', async () => {
    let rejectOldLike!: (error: unknown) => void;
    const oldLike = new Promise<InteractionState>((_, reject) => { rejectOldLike = reject; });
    const liked = { ...interactions, liked: true, likeCount: 9, canLike: false, canUnlike: true };
    likeApi.likePost
      .mockReturnValueOnce(oldLike)
      .mockResolvedValueOnce(liked);
    const { rerender } = renderDetail();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /좋아요 8/ }));

    auth.useOptionalAuth.mockReturnValue({ user: { ...viewer, id: 8, nickname: '새 계정' }, isLoading: false, isAuthenticated: true });
    rerender(
      <MemoryRouter initialEntries={['/blog/orbit/4']}>
        <Routes><Route path="/blog/:blogSlug/:postId" element={<PostDetailPage />} /></Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(likeApi.getInteractions).toHaveBeenCalledTimes(2));
    const nextLike = await screen.findByRole('button', { name: /좋아요 8/ });
    await waitFor(() => expect(nextLike).not.toBeDisabled());
    await user.click(nextLike);
    await waitFor(() => expect(likeApi.likePost).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(nextLike).toHaveAttribute('aria-pressed', 'true'));
    const interactionCallsBeforeStale = likeApi.getInteractions.mock.calls.length;

    await act(async () => {
      rejectOldLike(accessError('LIKE_001', 403));
      await Promise.resolve();
    });
    expect(likeApi.getInteractions).toHaveBeenCalledTimes(interactionCallsBeforeStale);
    expect(nextLike).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByText('현재 상태에서는 좋아요를 사용할 수 없습니다.')).not.toBeInTheDocument();
  });

  it('댓글 작성은 optimistic 행을 먼저 보여주고 canonical 응답으로 교체한다', async () => {
    const user = userEvent.setup();
    const saved = comment({ id: 12, content: '새 신호' });
    commentApi.listComments
      .mockResolvedValueOnce(page([]))
      .mockResolvedValueOnce(page([saved]));
    commentApi.createComment.mockImplementationOnce(() => new Promise((resolve) => {
      window.setTimeout(() => resolve(saved), 20);
    }));
    renderDetail();

    const input = await screen.findByRole('textbox', { name: '댓글 입력' });
    await user.type(input, '새 신호');
    await user.click(screen.getByRole('button', { name: '전송' }));

    expect(await screen.findByText('새 신호')).toBeInTheDocument();
    await waitFor(() => expect(commentApi.createComment).toHaveBeenCalledWith(4, {
      content: '새 신호',
      parentId: null,
    }));
    await waitFor(() => expect(input).toHaveValue(''));
    expect(commentApi.listComments).toHaveBeenCalledWith(4, { page: 0, size: 20 });
  });

  it('댓글 작성 실패는 optimistic 행을 롤백하고 입력을 보존한다', async () => {
    const user = userEvent.setup();
    commentApi.listComments.mockResolvedValueOnce(page([]));
    commentApi.createComment.mockImplementationOnce(() => new Promise((_, reject) => {
      window.setTimeout(() => reject(new Error('network')), 20);
    }));
    renderDetail();

    const input = await screen.findByRole('textbox', { name: '댓글 입력' });
    await user.type(input, '실패할 신호');
    await user.click(screen.getByRole('button', { name: '전송' }));
    expect(await screen.findByText('실패할 신호')).toBeInTheDocument();

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('댓글을 작성하지 못했습니다.'));
    expect(input).toHaveValue('실패할 신호');
  });

  it('댓글 mutation 성공 뒤 canonical GET이 실패해도 성공 DTO와 사용자 입력 결과를 유지한다', async () => {
    const user = userEvent.setup();
    const saved = comment({ id: 13, content: '서버 신호' });
    commentApi.listComments
      .mockResolvedValueOnce(page([]))
      .mockRejectedValueOnce(new Error('reload failed'))
      .mockResolvedValueOnce(page([saved]));
    commentApi.createComment.mockResolvedValueOnce(saved);
    renderDetail();

    const input = await screen.findByRole('textbox', { name: '댓글 입력' });
    await user.type(input, '서버 신호');
    await user.click(screen.getByRole('button', { name: '전송' }));
    await waitFor(() => expect(screen.getByText('서버 신호')).toBeInTheDocument());
    expect(await screen.findByRole('alert')).toHaveTextContent('변경사항은 저장됐지만 최신 댓글을 불러오지 못했습니다.');
    expect(document.querySelector('[data-comment-id^="-"]')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다시 시도' }));
    await waitFor(() => expect(commentApi.listComments).toHaveBeenCalledTimes(3));
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
    expect(screen.getByText('서버 신호')).toBeInTheDocument();
  });

  it('interactions 접근권한 상실 시 기존 좋아요 상태와 count를 폐기한다', async () => {
    const user = userEvent.setup();
    likeApi.getInteractions
      .mockResolvedValueOnce(interactions)
      .mockRejectedValueOnce(accessError('POST_002', 403));
    commentApi.listComments
      .mockResolvedValueOnce(page([comment()]))
      .mockResolvedValueOnce(page([comment({ content: '수정된 신호' })]));
    commentApi.updateComment.mockResolvedValueOnce(comment({ content: '수정된 신호' }));
    renderDetail();

    expect(await screen.findByRole('button', { name: /좋아요 8/ })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '댓글 11 수정' }));
    const edit = screen.getByRole('textbox', { name: '댓글 11 수정 내용' });
    await user.clear(edit);
    await user.type(edit, '수정된 신호');
    await user.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() => expect(likeApi.getInteractions).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole('button', { name: /좋아요/ })).not.toBeInTheDocument();
    expect(screen.getAllByRole('alert').some((element) => element.textContent?.includes('이 글을 볼 권한이 없습니다.'))).toBe(true);
    expect(screen.queryByText(/■ 댓글/)).not.toBeInTheDocument();
  });

  it.each([
    ['AUTH_004', 401],
    ['POST_002', 403],
    ['POST_001', 404],
  ])('초기 interactions GET %s 접근거부 시 댓글과 좋아요 상태를 노출하지 않는다', async (code, status) => {
    likeApi.getInteractions.mockRejectedValueOnce(accessError(code, status));
    renderDetail();

    await waitFor(() => expect(likeApi.getInteractions).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.queryByRole('button', { name: /좋아요/ })).not.toBeInTheDocument());
    expect(screen.queryByText(/■ 댓글/)).not.toBeInTheDocument();
  });

  it('삭제된 글의 좋아요 mutation POST_001은 interaction과 댓글 상태를 폐기한다', async () => {
    const user = userEvent.setup();
    likeApi.likePost.mockRejectedValueOnce(accessError('POST_001', 404));
    renderDetail();

    await user.click(await screen.findByRole('button', { name: /좋아요 8/ }));
    await waitFor(() => expect(likeApi.likePost).toHaveBeenCalledWith(4));
    expect(screen.queryByRole('button', { name: /좋아요/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/■ 댓글/)).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('글을 찾을 수 없습니다.');
  });

  it('댓글 mutation POST_002는 optimistic rollback 대신 interaction과 댓글 상태를 폐기한다', async () => {
    const user = userEvent.setup();
    commentApi.createComment.mockRejectedValueOnce(accessError('POST_002', 403));
    renderDetail();

    const input = await screen.findByRole('textbox', { name: '댓글 입력' });
    await user.type(input, '권한이 사라진 댓글');
    await user.click(screen.getByRole('button', { name: '전송' }));
    await waitFor(() => expect(commentApi.createComment).toHaveBeenCalledWith(4, {
      content: '권한이 사라진 댓글',
      parentId: null,
    }));
    expect(screen.queryByRole('button', { name: /좋아요/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/■ 댓글/)).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('이 글을 볼 권한이 없습니다.');
  });

  it('LIKE_001 좋아요 mutation은 interactions 재확인으로 canonical flags를 반영한다', async () => {
    const user = userEvent.setup();
    const canonical = { ...interactions, canLike: false, canUnlike: true };
    likeApi.getInteractions
      .mockResolvedValueOnce(interactions)
      .mockResolvedValueOnce(canonical);
    likeApi.likePost.mockRejectedValueOnce(accessError('LIKE_001', 403));
    renderDetail();

    expect(await screen.findByRole('button', { name: /좋아요 8/ })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /좋아요 8/ }));

    await waitFor(() => expect(likeApi.getInteractions).toHaveBeenCalledTimes(2));
    const like = screen.getByRole('button', { name: /좋아요 8/ });
    expect(like).toBeDisabled();
    expect(like).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('alert')).toHaveTextContent('현재 상태에서는 좋아요를 사용할 수 없습니다.');
  });

  it('LIKE_001 재확인 GET 권한거부는 좋아요와 댓글 상태를 폐기한다', async () => {
    const user = userEvent.setup();
    likeApi.getInteractions
      .mockResolvedValueOnce(interactions)
      .mockRejectedValueOnce(accessError('POST_002', 403));
    likeApi.likePost.mockRejectedValueOnce(accessError('LIKE_001', 403));
    renderDetail();

    await user.click(await screen.findByRole('button', { name: /좋아요 8/ }));
    await waitFor(() => expect(likeApi.getInteractions).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole('button', { name: /좋아요/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/■ 댓글/)).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('이 글을 볼 권한이 없습니다.');
  });

  it('LIKE_001 재확인 GET 일시 실패는 기존 상태와 retry 안내를 유지한다', async () => {
    const user = userEvent.setup();
    likeApi.getInteractions
      .mockResolvedValueOnce(interactions)
      .mockRejectedValueOnce(new Error('recheck network'))
      .mockResolvedValueOnce({ ...interactions, liked: false, likeCount: 8 });
    likeApi.likePost.mockRejectedValueOnce(accessError('LIKE_001', 403));
    renderDetail();

    await user.click(await screen.findByRole('button', { name: /좋아요 8/ }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('좋아요 상태를 다시 확인하지 못했습니다.'));
    expect(screen.getByRole('button', { name: /좋아요 8/ })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '다시 시도' }));
    await waitFor(() => expect(likeApi.getInteractions).toHaveBeenCalledTimes(3));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('댓글 canonical GET 접근권한 상실 시 기존 댓글을 폐기하고 부모에 알린다', async () => {
    const user = userEvent.setup();
    const accessDenied = accessError('POST_002', 403);
    const accessDeniedCallback = vi.fn();
    commentApi.listComments
      .mockResolvedValueOnce(page([comment()]))
      .mockRejectedValueOnce(accessDenied);
    commentApi.createComment.mockResolvedValueOnce(comment({ id: 13, content: '새 신호' }));
    const refresh = vi.fn().mockResolvedValue(undefined);
    render(
      <MemoryRouter>
        <CommentSection
          postId={4}
          viewer={viewer}
          interaction={interactions}
          interactionLoading={false}
          onRefreshInteractions={refresh}
          onInteractionAccessDenied={accessDeniedCallback}
        />
      </MemoryRouter>,
    );

    await screen.findByText('따뜻한 신호');
    const input = screen.getByRole('textbox', { name: '댓글 입력' });
    await user.type(input, '새 신호');
    await user.click(screen.getByRole('button', { name: '전송' }));

    await waitFor(() => expect(accessDeniedCallback).toHaveBeenCalledWith(accessDenied));
    expect(screen.queryByText('따뜻한 신호')).not.toBeInTheDocument();
    expect(screen.queryByText('새 신호')).not.toBeInTheDocument();
    expect(screen.getAllByRole('alert').some((element) => element.textContent?.includes('이 글의 댓글을 볼 권한이 없습니다.'))).toBe(true);
  });

  it('edit/delete 성공 뒤 canonical GET 실패는 성공 상태를 rollback하지 않는다', async () => {
    const user = userEvent.setup();
    const edited = comment({ content: '수정된 신호' });
    commentApi.listComments
      .mockResolvedValueOnce(page([comment()]))
      .mockRejectedValueOnce(new Error('edit reload failed'))
      .mockRejectedValueOnce(new Error('delete reload failed'));
    commentApi.updateComment.mockResolvedValueOnce(edited);
    commentApi.deleteComment.mockResolvedValueOnce(null);
    renderDetail();

    await screen.findByText('따뜻한 신호');
    await user.click(screen.getByRole('button', { name: '댓글 11 수정' }));
    const edit = screen.getByRole('textbox', { name: '댓글 11 수정 내용' });
    await user.clear(edit);
    await user.type(edit, '수정된 신호');
    await user.click(screen.getByRole('button', { name: '저장' }));
    expect(await screen.findByText('수정된 신호')).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toHaveTextContent('변경사항은 저장됐지만 최신 댓글을 불러오지 못했습니다.');

    await user.click(screen.getByRole('button', { name: '댓글 11 삭제' }));
    expect(await screen.findByText('삭제된 댓글')).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toHaveTextContent('변경사항은 저장됐지만 최신 댓글을 불러오지 못했습니다.');
    expect(screen.queryByText('수정된 신호')).not.toBeInTheDocument();
  });

  it('서버 canEdit/canDelete 계약에 따라 수정과 soft-delete를 canonical reload한다', async () => {
    const user = userEvent.setup();
    const edited = comment({ content: '수정된 신호' });
    const deleted = comment({ content: null, deleted: true, canEdit: false, canDelete: false, canReply: false });
    commentApi.listComments
      .mockResolvedValueOnce(page([comment()]))
      .mockResolvedValueOnce(page([edited]))
      .mockResolvedValueOnce(page([deleted]));
    commentApi.updateComment.mockResolvedValueOnce(edited);
    commentApi.deleteComment.mockResolvedValueOnce(null);
    renderDetail();

    await screen.findByText('따뜻한 신호');
    await user.click(screen.getByRole('button', { name: '댓글 11 수정' }));
    const edit = screen.getByRole('textbox', { name: '댓글 11 수정 내용' });
    await user.clear(edit);
    await user.type(edit, '수정된 신호');
    await user.click(screen.getByRole('button', { name: '저장' }));
    await waitFor(() => expect(commentApi.updateComment).toHaveBeenCalledWith(11, { content: '수정된 신호' }));
    expect(await screen.findByText('수정된 신호')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '댓글 11 삭제' }));
    await waitFor(() => expect(commentApi.deleteComment).toHaveBeenCalledWith(11));
    expect(await screen.findByText('삭제된 댓글')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '댓글 11 수정' })).not.toBeInTheDocument();
  });

  it('guest는 댓글 입력 대신 로그인 CTA를 보고 상호작용 API의 권한을 따른다', async () => {
    auth.useOptionalAuth.mockReturnValue({ user: null, isLoading: false, isAuthenticated: false });
    likeApi.getInteractions.mockResolvedValueOnce({
      ...interactions,
      liked: false,
      canComment: false,
      canLike: false,
      canUnlike: false,
    });
    commentApi.listComments.mockResolvedValueOnce(page([]));
    renderDetail();

    expect(await screen.findByRole('link', { name: '로그인 후 댓글을 남겨주세요.' })).toHaveAttribute('href', '/signin');
    expect(screen.queryByRole('textbox', { name: '댓글 입력' })).not.toBeInTheDocument();
    expect(await screen.findByRole('link', { name: /좋아요 8/ })).toHaveAttribute('href', '/signin');
  });

  it('작성자와 서버 capability가 다르면 edit/delete/reply action을 노출하지 않는다', async () => {
    const other = comment({
      author: { id: 8, nickname: '다른 사람', profileImageUrl: null },
      canEdit: false,
      canDelete: false,
      canReply: false,
    });
    const reply = comment({ id: 12, parentId: 11, canEdit: false, canDelete: false, canReply: false });
    const deletedRoot = comment({ id: 10, author: null, content: null, deleted: true, canEdit: false, canDelete: false, canReply: false, replies: page([reply]) });
    commentApi.listComments.mockResolvedValueOnce(page([other, deletedRoot]));
    renderDetail();

    expect(await screen.findByText('다른 사람')).toBeInTheDocument();
    expect(screen.getByText('삭제된 댓글')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '댓글 11 수정' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '댓글 11 삭제' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '댓글 11 답글' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '댓글 10 답글' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '댓글 12 답글' })).not.toBeInTheDocument();
  });

  it('post owner는 author가 아니어도 서버 canDelete=true 댓글을 삭제할 수 있다', async () => {
    const user = userEvent.setup();
    const other = comment({
      author: { id: 8, nickname: '다른 사람', profileImageUrl: null },
      canEdit: false,
      canDelete: true,
      canReply: false,
    });
    const deleted = comment({ ...other, content: null, deleted: true, canDelete: false });
    commentApi.listComments.mockResolvedValueOnce(page([other])).mockResolvedValueOnce(page([deleted]));
    commentApi.deleteComment.mockResolvedValueOnce(null);
    renderDetail();

    await screen.findByText('다른 사람');
    await user.click(screen.getByRole('button', { name: '댓글 11 삭제' }));
    await waitFor(() => expect(commentApi.deleteComment).toHaveBeenCalledWith(11));
    expect(await screen.findByText('삭제된 댓글')).toBeInTheDocument();
  });

  it('늦은 댓글 GET 응답은 계정 전환 뒤 새 댓글 목록을 덮지 않는다', async () => {
    let resolveOld!: (value: PageResponse<CommentItem>) => void;
    const oldPage = new Promise<PageResponse<CommentItem>>((resolve) => { resolveOld = resolve; });
    const oldComment = comment({ id: 20, content: '이전 계정 댓글' });
    const newComment = comment({ id: 21, content: '새 계정 댓글' });
    commentApi.listComments
      .mockReturnValueOnce(oldPage)
      .mockResolvedValueOnce(page([newComment]));
    const refresh = vi.fn().mockResolvedValue(undefined);
    const { rerender } = render(
      <MemoryRouter>
        <CommentSection
          postId={4}
          viewer={viewer}
          interaction={interactions}
          interactionLoading={false}
          onRefreshInteractions={refresh}
        />
      </MemoryRouter>,
    );
    await waitFor(() => expect(commentApi.listComments).toHaveBeenCalledTimes(1));

    rerender(
      <MemoryRouter>
        <CommentSection
          postId={4}
          viewer={{ ...viewer, id: 8, nickname: '새 계정' }}
          interaction={interactions}
          interactionLoading={false}
          onRefreshInteractions={refresh}
        />
      </MemoryRouter>,
    );

    expect(await screen.findByText('새 계정 댓글')).toBeInTheDocument();
    resolveOld(page([oldComment]));
    await new Promise((resolve) => window.setTimeout(resolve, 0));
    expect(screen.queryByText('이전 계정 댓글')).not.toBeInTheDocument();
  });

  it('늦은 canonical 상호작용 거부는 글·계정 전환 뒤 새 댓글과 mutation을 지우지 않는다', async () => {
    const user = userEvent.setup();
    const oldSaved = comment({ id: 30, content: '이전 저장 댓글' });
    const newSaved = comment({ id: 31, content: '새 저장 댓글' });
    const oldAccessDenied = accessError('POST_002', 403);
    let resolveOldRefresh!: () => void;
    let rejectOldRefresh!: (error: unknown) => void;
    const oldRefreshPending = new Promise<void>((resolve, reject) => {
      resolveOldRefresh = resolve;
      rejectOldRefresh = reject;
    });
    const oldRefresh = vi.fn(() => oldRefreshPending);
    const newRefresh = vi.fn().mockResolvedValue(undefined);
    commentApi.listComments
      .mockResolvedValueOnce(page([comment()]))
      .mockResolvedValueOnce(page([oldSaved]))
      .mockResolvedValueOnce(page([comment({ id: 21, content: '새 화면 댓글' })]))
      .mockResolvedValueOnce(page([newSaved]));
    commentApi.createComment
      .mockResolvedValueOnce(oldSaved)
      .mockResolvedValueOnce(newSaved);

    const { rerender } = render(
      <MemoryRouter>
        <CommentSection
          postId={4}
          viewer={viewer}
          interaction={interactions}
          interactionLoading={false}
          onRefreshInteractions={oldRefresh}
          onInteractionAccessDenied={vi.fn()}
        />
      </MemoryRouter>,
    );

    const input = await screen.findByRole('textbox', { name: '댓글 입력' });
    await user.type(input, '이전 저장 댓글');
    await user.click(screen.getByRole('button', { name: '전송' }));
    await waitFor(() => expect(oldRefresh).toHaveBeenCalledTimes(1));

    const accessDeniedCallback = vi.fn();
    rerender(
      <MemoryRouter>
        <CommentSection
          postId={5}
          viewer={{ ...viewer, id: 8, nickname: '새 계정' }}
          interaction={interactions}
          interactionLoading={false}
          onRefreshInteractions={newRefresh}
          onInteractionAccessDenied={accessDeniedCallback}
        />
      </MemoryRouter>,
    );

    expect(await screen.findByText('새 화면 댓글')).toBeInTheDocument();
    await act(async () => {
      rejectOldRefresh(oldAccessDenied);
      await Promise.resolve();
    });
    expect(accessDeniedCallback).not.toHaveBeenCalled();
    expect(screen.getByText('새 화면 댓글')).toBeInTheDocument();

    await user.type(screen.getByRole('textbox', { name: '댓글 입력' }), '새 저장 댓글');
    await user.click(screen.getByRole('button', { name: '전송' }));
    await waitFor(() => expect(commentApi.createComment).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('새 저장 댓글')).toBeInTheDocument();
    resolveOldRefresh();
  });

  it('root page를 왕복하는 동안 늦은 reply GET이 새 root 카드에 재주입되지 않는다', async () => {
    const user = userEvent.setup();
    let resolveReply!: (value: PageResponse<CommentItem>) => void;
    const delayedReply = new Promise<PageResponse<CommentItem>>((resolve) => { resolveReply = resolve; });
    const rootZero = comment({ id: 11, content: 'root-zero', canReply: false, replies: page([], { totalElements: 21, totalPages: 2, hasNext: true }) });
    const rootOne = comment({ id: 22, content: 'root-one', canReply: false, replies: page([], { totalElements: 0, totalPages: 1 }) });
    const freshReply = comment({ id: 98, parentId: 11, content: 'fresh reply', canReply: false });
    const nextReply = comment({ id: 97, parentId: 11, content: 'next reply', canReply: false });
    const rootZeroAgain = comment({ id: 11, content: 'root-zero-fresh', canReply: false, replies: page([freshReply], { totalElements: 21, totalPages: 2, hasNext: true }) });
    const staleReply = comment({ id: 99, parentId: 11, content: 'stale reply', canReply: false });
    commentApi.listComments
      .mockResolvedValueOnce(page([rootZero], { totalPages: 2, hasNext: true }))
      .mockReturnValueOnce(delayedReply)
      .mockResolvedValueOnce(page([rootOne], { page: 1, totalPages: 2, hasPrevious: true }))
      .mockResolvedValueOnce(page([rootZeroAgain], { totalPages: 2, hasNext: true }))
      .mockResolvedValueOnce(page([nextReply], { page: 1, totalPages: 2, hasPrevious: true }));
    const refresh = vi.fn().mockResolvedValue(undefined);
    render(
      <MemoryRouter>
        <CommentSection
          postId={4}
          viewer={viewer}
          interaction={interactions}
          interactionLoading={false}
          onRefreshInteractions={refresh}
        />
      </MemoryRouter>,
    );

    expect(await screen.findByText('root-zero')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '댓글 11 답글 다음 페이지' }));
    await user.click(screen.getByRole('button', { name: '댓글 다음 페이지' }));
    expect(await screen.findByText('root-one')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '댓글 이전 페이지' }));
    expect(await screen.findByText('root-zero-fresh')).toBeInTheDocument();
    expect(await screen.findByText('fresh reply')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '댓글 11 답글 다음 페이지' }));
    expect(await screen.findByText('next reply')).toBeInTheDocument();

    resolveReply(page([staleReply]));
    await new Promise((resolve) => window.setTimeout(resolve, 0));
    expect(screen.queryByText('stale reply')).not.toBeInTheDocument();
  });
});
