import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../lib/authContext';
import { resetApiClient } from '../lib/apiClient';
import { GuestOnlyRoute, ProtectedRoute, SetupGuard } from '../routes/guards';
import type { AuthUser } from '../types/auth';

/** 라우팅 가드(PRD §8.3). */

const SETUP_DONE: AuthUser = {
  id: 1,
  email: 'user@zeroverse.test',
  name: '테스터',
  nickname: 'tester',
  role: 'USER',
  profileImageUrl: null,
  defaultBlog: { id: 1, title: '테스터의 블로그', urlSlug: 'tester', isSetupCompleted: true },
};

const SETUP_PENDING: AuthUser = {
  ...SETUP_DONE,
  defaultBlog: { ...SETUP_DONE.defaultBlog, isSetupCompleted: false },
};

function envelope(data: unknown) {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ success: true, data, error: null, timestamp: '' }),
    json: async () => ({ success: true, data, error: null, timestamp: '' }),
  } as unknown as Response;
}

function unauthorized() {
  return {
    ok: false,
    status: 401,
    text: async () =>
      JSON.stringify({
        success: false,
        data: null,
        error: { code: 'AUTH_003', message: '유효하지 않은 Refresh Token입니다.', details: [] },
        timestamp: '',
      }),
    json: async () => ({ success: false, data: null, error: { code: 'AUTH_003' }, timestamp: '' }),
  } as unknown as Response;
}

/** 세션 복구 결과를 정해 렌더한다. */
function renderWithSession(
  user: AuthUser | null,
  initialPath: string,
  children: React.ReactNode,
) {
  const fetchMock = vi.fn(async (url: string) => {
    if (url.includes('/auth/refresh')) {
      return user ? envelope({ accessToken: 't', tokenType: 'Bearer', expiresIn: 3600 }) : unauthorized();
    }
    if (url.includes('/auth/me')) {
      return envelope(user);
    }
    return envelope(null);
  });
  vi.stubGlobal('fetch', fetchMock);

  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <Routes>
          <Route path={initialPath} element={children} />
          <Route path="/" element={<p>홈</p>} />
          <Route path="/signin" element={<p>로그인 화면</p>} />
          <Route path="/blog/setup" element={<p>초기 설정</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('라우팅 가드', () => {
  beforeEach(() => resetApiClient());
  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  describe('ProtectedRoute', () => {
    it('미인증이면 /signin으로 보낸다', async () => {
      renderWithSession(null, '/notifications', <ProtectedRoute><p>알림</p></ProtectedRoute>);

      await waitFor(() => expect(screen.getByText('로그인 화면')).toBeInTheDocument());
      expect(screen.queryByText('알림')).not.toBeInTheDocument();
    });

    it('인증되면 자식을 렌더한다', async () => {
      renderWithSession(SETUP_DONE, '/notifications', <ProtectedRoute><p>알림</p></ProtectedRoute>);

      await waitFor(() => expect(screen.getByText('알림')).toBeInTheDocument());
    });

    it('세션 복구 중에는 리다이렉트하지 않는다 (깜빡임 방지)', () => {
      const { container } = renderWithSession(
        SETUP_DONE,
        '/notifications',
        <ProtectedRoute><p>알림</p></ProtectedRoute>,
      );

      // 아직 refresh 응답 전 — 대기 상태여야 한다.
      expect(container.querySelector('[data-guard-pending="true"]')).not.toBeNull();
      expect(screen.queryByText('로그인 화면')).not.toBeInTheDocument();
    });
  });

  describe('GuestOnlyRoute', () => {
    it('비로그인은 그대로 보여준다', async () => {
      renderWithSession(null, '/signin', <GuestOnlyRoute><p>로그인 폼</p></GuestOnlyRoute>);

      await waitFor(() => expect(screen.getByText('로그인 폼')).toBeInTheDocument());
    });

    it('설정을 마친 사용자는 /로 보낸다', async () => {
      renderWithSession(SETUP_DONE, '/signin', <GuestOnlyRoute><p>로그인 폼</p></GuestOnlyRoute>);

      await waitFor(() => expect(screen.getByText('홈')).toBeInTheDocument());
    });

    it('설정을 안 마친 사용자는 /blog/setup으로 보낸다', async () => {
      renderWithSession(SETUP_PENDING, '/signin', <GuestOnlyRoute><p>로그인 폼</p></GuestOnlyRoute>);

      await waitFor(() => expect(screen.getByText('초기 설정')).toBeInTheDocument());
    });
  });

  describe('SetupGuard', () => {
    it('미인증이면 /signin으로 보낸다', async () => {
      renderWithSession(null, '/write', <SetupGuard><p>글쓰기</p></SetupGuard>);

      await waitFor(() => expect(screen.getByText('로그인 화면')).toBeInTheDocument());
    });

    it('설정 미완료 사용자가 다른 화면에 가면 /blog/setup으로 모은다', async () => {
      renderWithSession(SETUP_PENDING, '/write', <SetupGuard><p>글쓰기</p></SetupGuard>);

      await waitFor(() => expect(screen.getByText('초기 설정')).toBeInTheDocument());
      expect(screen.queryByText('글쓰기')).not.toBeInTheDocument();
    });

    it('설정 완료 사용자는 보호 화면을 볼 수 있다', async () => {
      renderWithSession(SETUP_DONE, '/write', <SetupGuard><p>글쓰기</p></SetupGuard>);

      await waitFor(() => expect(screen.getByText('글쓰기')).toBeInTheDocument());
    });

    it('이미 설정을 마쳤는데 /blog/setup에 오면 /로 보낸다', async () => {
      renderWithSession(SETUP_DONE, '/blog/setup', <SetupGuard><p>설정 폼</p></SetupGuard>);

      await waitFor(() => expect(screen.getByText('홈')).toBeInTheDocument());
    });

    it('설정 미완료 사용자는 /blog/setup에 머문다', async () => {
      renderWithSession(SETUP_PENDING, '/blog/setup', <SetupGuard><p>설정 폼</p></SetupGuard>);

      await waitFor(() => expect(screen.getByText('설정 폼')).toBeInTheDocument());
    });
  });
});
