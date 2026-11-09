import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../lib/authContext';
import { useAuth } from '../lib/authContext';
import { MainPage } from '../pages/MainPage';
import { SearchPage } from '../pages/SearchPage';
import { resetApiClient } from '../lib/apiClient';

const AUTH_USER = {
  id: 7,
  email: 'seven@zeroverse.test',
  name: '세븐',
  nickname: '세븐별',
  role: 'USER' as const,
  profileImageUrl: null,
  defaultBlog: { id: 17, title: '세븐별의 기록', urlSlug: 'seven', isSetupCompleted: true },
};

function envelope<T>(data: T, status = 200) {
  return new Response(
    JSON.stringify({ success: status < 400, data: status < 400 ? data : null, error: status < 400 ? null : data, timestamp: '' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

function expiredApiFetch() {
  const calls: string[] = [];
  const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const path = new URL(String(input)).pathname;
    const method = init?.method ?? 'GET';
    calls.push(`${method} ${path}`);
    if (path === '/api/v1/auth/refresh') {
      const refreshCount = calls.filter((call) => call.endsWith('/api/v1/auth/refresh')).length;
      return refreshCount === 1
        ? envelope({ accessToken: 'access-token' })
        : envelope({ code: 'AUTH_002', message: '세션이 만료되었습니다.', details: [] }, 401);
    }
    if (path === '/api/v1/auth/me') return envelope(AUTH_USER);
    if (path === '/api/v1/feed/universe' || path === '/api/v1/search') {
      return envelope({ code: 'AUTH_002', message: '세션이 만료되었습니다.', details: [] }, 401);
    }
    throw new Error(`Unexpected request: ${method} ${path}`);
  });
  return { calls, fetch };
}

function renderWithAuth(node: ReactNode, initialEntry: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[initialEntry]}>{node}</MemoryRouter>
    </AuthProvider>,
  );
}

function SignoutButton() {
  const { signout } = useAuth();
  return <button type="button" onClick={() => void signout()}>로그아웃</button>;
}

describe('M7 auth expiry integration', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    resetApiClient();
  });

  it('MainPage는 refresh 최종 실패 뒤 guest public 재조회를 하지 않고 만료 오류를 유지한다', async () => {
    const { calls } = expiredApiFetch();
    renderWithAuth(<MainPage />, '/');

    expect(await screen.findByRole('alert')).toHaveTextContent('세션이 만료되었습니다');
    await waitFor(() => expect(calls.filter((call) => call.includes('/api/v1/feed/public')).length).toBe(0));
    expect(calls.filter((call) => call.includes('/api/v1/feed/universe')).length).toBe(1);
  });

  it('SearchPage도 refresh 최종 실패 뒤 guest 검색 재조회를 하지 않고 만료 오류를 유지한다', async () => {
    const { calls } = expiredApiFetch();
    renderWithAuth(<SearchPage />, '/search?keyword=QueryDSL');

    expect(await screen.findByRole('alert')).toHaveTextContent('세션이 만료되었습니다');
    await waitFor(() => expect(calls.filter((call) => call.includes('/api/v1/search')).length).toBe(1));
  });

  it('명시 로그아웃은 만료 상태가 아니므로 MainPage가 guest public 피드로 전환한다', async () => {
    const calls: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const path = new URL(String(input)).pathname;
      const method = init?.method ?? 'GET';
      calls.push(`${method} ${path}`);
      if (path === '/api/v1/auth/refresh') return envelope({ accessToken: 'access-token' });
      if (path === '/api/v1/auth/me') return envelope(AUTH_USER);
      if (path === '/api/v1/auth/signout') return new Response(null, { status: 204 });
      if (path === '/api/v1/feed/universe' || path === '/api/v1/feed/public') {
        return envelope({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false });
      }
      throw new Error(`Unexpected request: ${method} ${path}`);
    });
    const user = (await import('@testing-library/user-event')).default.setup();
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/']}>
          <MainPage />
          <SignoutButton />
        </MemoryRouter>
      </AuthProvider>,
    );

    await screen.findByText('0개의 신호');
    await user.click(screen.getByRole('button', { name: '로그아웃' }));
    await waitFor(() => expect(calls.filter((call) => call.includes('/api/v1/feed/public')).length).toBe(1));
    expect(screen.queryByText('세션이 만료되었습니다. 다시 로그인해 주세요.')).not.toBeInTheDocument();
  });
});
