import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AppRoutes } from '../routes/router';
import { AuthProvider } from '../lib/authContext';
import { resetApiClient } from '../lib/apiClient';

/**
 * 라우트가 대응 화면을 렌더하는지 확인한다.
 *
 * <p>M1에서 가드가 붙어 보호 경로는 로그인 상태여야 도달한다. 여기서는 **초기 설정까지 마친
 * 사용자**로 세션을 고정해 라우팅 자체를 검증한다. 가드 분기는 `guards.test.tsx`가 맡는다.
 */
const AUTHENTICATED_USER = {
  id: 1,
  email: 'router@zeroverse.test',
  name: '테스터',
  nickname: 'router',
  role: 'USER' as const,
  profileImageUrl: null,
  defaultBlog: { id: 1, title: '테스터의 블로그', urlSlug: 'router', isSetupCompleted: true },
};

function stubSession(user: typeof AUTHENTICATED_USER | null) {
  const categoryPage = {
    items: [
      {
        id: 1,
        parentId: null,
        name: '미분류',
        type: 'DEFAULT',
        displayOrder: 0,
        postCount: 0,
        children: [],
      },
    ],
    page: 0,
    size: 100,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  };
  const post = {
    id: 42,
    blogId: 1,
    blogSlug: 'my-blog',
    blogTitle: '테스터의 블로그',
    author: { id: 1, nickname: 'router', profileImageUrl: null },
    category: { id: 1, name: '미분류' },
    title: '게시글',
    thumbnailUrl: null,
    visibility: 'PUBLIC',
    tags: [],
    excerpt: '라우팅 테스트 글',
    publishedAt: '2026-01-01T00:00:00Z',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    contentHtml: '<p>라우팅 테스트 글</p>',
    contentJson: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: '라우팅 테스트 글' }] }] },
    images: [],
    viewCount: 0,
    previous: null,
    next: null,
  };
  const alternatePost = {
    ...post,
    id: 43,
    title: '다른 초안',
    contentHtml: '<p>다른 초안 본문</p>',
    contentJson: {
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: '다른 초안 본문' }] }],
    },
    visibility: 'PRIVATE',
    publishedAt: null,
  };
  const draftPage = {
    items: [{ ...alternatePost, excerpt: '다른 초안 본문' }],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  };
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      const body = (data: unknown, success = true) => ({
        ok: success,
        status: success ? 200 : 401,
        text: async () =>
          JSON.stringify({ success, data, error: success ? null : { code: 'AUTH_003', details: [] }, timestamp: '' }),
        json: async () => ({ success, data, error: null, timestamp: '' }),
      });
      if (url.includes('/auth/refresh')) {
        return user
          ? body({ accessToken: 't', tokenType: 'Bearer', expiresIn: 3600 })
          : body(null, false);
      }
      if (url.includes('/auth/me')) return body(user);
      if (url.includes('/api/v1/posts/drafts')) return body(draftPage);
      if (url.includes('/api/v1/posts/43')) return body(alternatePost);
      if (url.includes('/api/v1/posts/42')) return body(post);
      if (url.includes('/api/v1/blogs/1/categories')) return body(categoryPage);
      return body(null);
    }),
  );
}

function renderAt(path: string) {
  stubSession(AUTHENTICATED_USER);
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </MemoryRouter>,
  );
}

/** 인증 사용자로 접근하는 경로. */
const ROUTES: Array<[string, string, 'heading' | 'textbox']> = [
  ['/', '유니버스 새 소식', 'heading'],
  ['/blog/my-blog', '블로그', 'heading'],
  ['/blog/my-blog/42', '게시글', 'heading'],
  ['/write', '제목', 'textbox'],
  ['/edit/42', '제목', 'textbox'],
  ['/settings', '프로필 설정', 'heading'],
  ['/settings/universe', '유니버스 관리', 'heading'],
  ['/settings/posts', '글·카테고리 관리', 'heading'],
  ['/search', '검색', 'heading'],
  ['/notifications', '알림 센터', 'heading'],
  ['/admin', '관리자', 'heading'],
  ['/admin/users/7', '사용자 상세', 'heading'],
];

describe('AppRoutes', () => {
  beforeEach(() => resetApiClient());
  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it.each(ROUTES)('%s 는 "%s" 화면을 렌더한다', async (path, name, role) => {
    renderAt(path);
    await waitFor(() => {
      if (role === 'textbox') {
        expect(screen.getByRole('textbox', { name })).toBeInTheDocument();
      } else {
        expect(screen.getByRole('heading', { level: 1, name })).toBeInTheDocument();
      }
    });
  });

  it('수정 화면의 초안 선택은 새 ID로 이동하고 전체 snapshot을 다시 불러온다', async () => {
    const user = userEvent.setup();
    renderAt('/edit/42');
    await waitFor(() => expect(screen.getByRole('textbox', { name: '제목' })).toHaveValue('게시글'));

    await user.click(screen.getByRole('button', { name: '임시저장 가져오기' }));
    await user.click(await screen.findByRole('button', { name: '다른 초안' }));

    await waitFor(() => expect(screen.getByRole('textbox', { name: '제목' })).toHaveValue('다른 초안'));
    expect(screen.getByTestId('post-editor-surface')).toHaveTextContent('다른 초안 본문');
  });

  it.each([
    ['/signin', '로그인'],
    ['/signup', '회원가입'],
  ])('%s 는 비로그인 상태에서 "%s" 탭이 활성이다', async (path, tab) => {
    stubSession(null);
    render(
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <AppRoutes />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() =>
      expect(screen.getByRole('tab', { name: tab })).toHaveAttribute('aria-selected', 'true'),
    );
  });

  it('15개 라우트를 모두 커버한다 (인증 12 + 게스트 2 + not-found 1)', () => {
    expect(ROUTES).toHaveLength(12);
  });

  it('알 수 없는 경로는 명시적 not-found 화면을 렌더한다', async () => {
    renderAt('/this/does/not/exist');
    await waitFor(() =>
      expect(
        screen.getByRole('heading', { level: 1, name: '페이지를 찾을 수 없습니다' }),
      ).toBeInTheDocument(),
    );
  });
});
