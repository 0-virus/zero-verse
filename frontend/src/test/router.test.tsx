import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
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
const ROUTES: Array<[string, string]> = [
  ['/', '유니버스 새 소식'],
  ['/blog/my-blog', '블로그'],
  ['/blog/my-blog/42', '게시글'],
  ['/write', '글쓰기'],
  ['/edit/42', '글 수정'],
  ['/settings', '프로필 설정'],
  ['/settings/universe', '유니버스 관리'],
  ['/settings/posts', '글·카테고리 관리'],
  ['/search', '검색'],
  ['/notifications', '알림 센터'],
  ['/admin', '관리자'],
  ['/admin/users/7', '사용자 상세'],
];

describe('AppRoutes', () => {
  beforeEach(() => resetApiClient());
  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it.each(ROUTES)('%s 는 "%s" 화면을 렌더한다', async (path, heading) => {
    renderAt(path);
    await waitFor(() =>
      expect(screen.getByRole('heading', { level: 1, name: heading })).toBeInTheDocument(),
    );
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
