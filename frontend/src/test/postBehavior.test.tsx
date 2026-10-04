import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { BlogPage } from '../pages/BlogPage';
import { PostDetailPage } from '../pages/PostDetailPage';
import { HeroBlogProvider } from '../lib/heroBlogContext';
import type { AuthUser } from '../types/auth';
import type { PageResponse, PostDetail, PostSummary } from '../features/post/types';

const postApi = vi.hoisted(() => ({
  listBlogPostsBySlug: vi.fn(),
  getPost: vi.fn(),
  deletePost: vi.fn(),
}));
const blogApi = vi.hoisted(() => ({
  getPublicBlog: vi.fn(),
}));
const auth = vi.hoisted(() => ({
  useAuth: vi.fn(),
  useOptionalAuth: vi.fn(),
}));
let clipboardWrite: ReturnType<typeof vi.fn>;

vi.mock('../features/post/postApi', () => postApi);
vi.mock('../features/blog/blogApi', () => blogApi);
vi.mock('../lib/authContext', () => auth);

const viewer: AuthUser = {
  id: 9,
  email: 'owner@example.com',
  name: '소유자',
  nickname: 'owner',
  role: 'USER',
  profileImageUrl: null,
  defaultBlog: { id: 2, title: '궤도역학', urlSlug: 'orbit', isSetupCompleted: true },
};

const summary: PostSummary = {
  id: 4,
  blogId: 2,
  blogSlug: 'orbit',
  blogTitle: '궤도역학',
  author: { id: 9, nickname: 'owner', profileImageUrl: null },
  category: { id: 3, name: '개발' },
  title: '첫 로그',
  thumbnailUrl: null,
  visibility: 'PUBLIC',
  viewCount: 12,
  publishedAt: '2026-10-03T00:00:00Z',
  createdAt: '2026-10-03T00:00:00Z',
  updatedAt: '2026-10-03T00:00:00Z',
  tags: ['react'],
  excerpt: '공개된 첫 로그입니다.',
};

const detail: PostDetail = {
  ...summary,
  contentJson: {
    type: 'doc',
    content: [
      { type: 'heading', attrs: { level: 2 }, content: [{ type: 'text', text: '본문 제목' }] },
      {
        type: 'paragraph',
        content: [
          { type: 'text', text: '안전한 본문' },
          { type: 'text', text: ' 강조', marks: [{ type: 'bold' }] },
        ],
      },
    ],
  },
  contentHtml: '<h2>본문 제목</h2><p>안전한 본문 <strong>강조</strong></p>',
  images: [],
  previous: { id: 3, title: '이전 로그', blogSlug: 'orbit' },
  next: { id: 5, title: '다음 로그', blogSlug: 'orbit' },
};

const publicBlog = {
  id: 2,
  title: '궤도역학',
  urlSlug: 'orbit',
  description: '별과 코드를 기록합니다.',
  owner: { id: 9, nickname: 'owner', profileImageUrl: null, bio: null },
};

function page(items: PostSummary[], pageNumber = 0): PageResponse<PostSummary> {
  return {
    items,
    page: pageNumber,
    size: 1,
    totalElements: 2,
    totalPages: 2,
    hasNext: pageNumber === 0,
    hasPrevious: pageNumber > 0,
  };
}

function renderBlog(initialEntry = '/blog/orbit?categoryId=3&sort=popular&page=0') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <HeroBlogProvider>
        <Routes>
          <Route path="/blog/:blogSlug" element={<BlogPage />} />
        </Routes>
      </HeroBlogProvider>
    </MemoryRouter>,
  );
}

function renderDetail() {
  return render(
    <MemoryRouter initialEntries={['/blog/orbit/4']}>
      <HeroBlogProvider>
        <Routes>
          <Route path="/blog/:blogSlug/:postId" element={<PostDetailPage />} />
          <Route path="/edit/:postId" element={<p>편집 화면</p>} />
          <Route path="/blog/:blogSlug" element={<p>블로그 목록</p>} />
        </Routes>
      </HeroBlogProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  auth.useAuth.mockReturnValue({ user: viewer, isLoading: false, isAuthenticated: true });
  auth.useOptionalAuth.mockReturnValue({ user: viewer, isLoading: false, isAuthenticated: true });
  blogApi.getPublicBlog.mockResolvedValue(publicBlog);
  postApi.listBlogPostsBySlug.mockResolvedValue(page([summary]));
  postApi.getPost.mockResolvedValue(detail);
  postApi.deletePost.mockResolvedValue(null);
  clipboardWrite = vi.fn().mockResolvedValue(undefined);
  Object.defineProperty(window.navigator, 'clipboard', {
    configurable: true,
    writable: true,
    value: { writeText: clipboardWrite },
  });
  vi.stubGlobal('confirm', vi.fn(() => true));
});

afterEach(() => {
  vi.clearAllMocks();
  vi.unstubAllGlobals();
});

describe('M4 BlogPage/PostDetailPage 행동', () => {
  it('URL query의 category/sort/page를 실제 slug 목록 API와 동기화하고 다음 페이지로 이동한다', async () => {
    const user = userEvent.setup();
    renderBlog();

    expect(await screen.findByRole('heading', { level: 2, name: '첫 로그' })).toBeInTheDocument();
    expect(postApi.listBlogPostsBySlug).toHaveBeenCalledWith('orbit', {
      page: 0,
      size: 10,
      sort: 'popular',
      categoryId: 3,
    });

    await user.click(screen.getByRole('button', { name: '다음 페이지' }));
    await waitFor(() => expect(postApi.listBlogPostsBySlug).toHaveBeenCalledWith('orbit', {
      page: 1,
      size: 10,
      sort: 'popular',
      categoryId: 3,
    }));
  });

  it('상세가 실제 JSON 본문·읽기 메타·공유 URL·owner 수정/삭제만 제공한다', async () => {
    const user = userEvent.setup();
    clipboardWrite = vi.spyOn(window.navigator.clipboard, 'writeText').mockResolvedValue(undefined);
    renderDetail();

    expect(await screen.findByRole('heading', { level: 1, name: '첫 로그' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: '본문 제목' })).toBeInTheDocument();
    expect(screen.getByText('안전한 본문')).toBeInTheDocument();
    expect(screen.getByText(/조회 12/)).toBeInTheDocument();
    expect(screen.getByText(/읽는 데/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /좋아요/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/댓글/)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '공유' }));
    expect(clipboardWrite).toHaveBeenCalledWith(window.location.href);
    expect(await screen.findByText('링크를 복사했습니다.')).toBeInTheDocument();

    expect(screen.getByRole('link', { name: '수정' })).toHaveAttribute('href', '/edit/4');
    await user.click(screen.getByRole('button', { name: '삭제' }));
    await waitFor(() => expect(postApi.deletePost).toHaveBeenCalledWith(4));
    expect(await screen.findByText('블로그 목록')).toBeInTheDocument();
  });
});
