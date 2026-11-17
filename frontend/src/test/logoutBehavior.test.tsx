import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { TopBar } from '../components/layout/TopBar';
import { AuthProvider, useAuth } from '../lib/authContext';
import { getAccessToken, resetApiClient } from '../lib/apiClient';

const USER = {
  id: 7,
  email: 'logout@zeroverse.test',
  name: '로그아웃 테스트',
  nickname: '세븐별',
  role: 'USER' as const,
  profileImageUrl: null,
  defaultBlog: { id: 17, title: '세븐별의 기록', urlSlug: 'seven', isSetupCompleted: true },
};

function envelope(data: unknown, status = 200) {
  const success = status < 400;
  return new Response(
    JSON.stringify({
      success,
      data: success ? data : null,
      error: success ? null : { code: 'AUTH_004', message: '로그아웃 요청에 실패했습니다.', details: [] },
      timestamp: '',
    }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="logout-location">{location.pathname}</output>;
}

function AuthProbe() {
  const { isAuthenticated } = useAuth();
  return <output data-testid="logout-auth-state">{isAuthenticated ? 'authenticated' : 'signed-out'}</output>;
}

function TopBarHarness() {
  const { user } = useAuth();
  return <TopBar nickname={user?.nickname} />;
}

function renderHarness(initialEntry = '/settings') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AuthProvider>
        <TopBarHarness />
        <LocationProbe />
        <AuthProbe />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('TopBar 로그아웃 실제 AuthContext 연동', () => {
  beforeEach(() => resetApiClient());

  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it.each([204, 500])('서버가 %s를 반환해도 세션을 정리하고 /signin으로 이동한다', async (signoutStatus) => {
    const calls: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = new URL(String(input)).pathname;
        calls.push(`${init?.method ?? 'GET'} ${path}`);
        if (path === '/api/v1/auth/refresh') return envelope({ accessToken: 'access-token' });
        if (path === '/api/v1/auth/me') return envelope(USER);
        if (path === '/api/v1/auth/signout') {
          return signoutStatus === 204 ? new Response(null, { status: 204 }) : envelope(null, signoutStatus);
        }
        return envelope(null);
      }),
    );

    renderHarness();
    await screen.findByRole('link', { name: USER.nickname });
    expect(screen.getByTestId('logout-auth-state')).toHaveTextContent('authenticated');
    expect(getAccessToken()).toBe('access-token');

    const user = userEvent.setup();
    await user.click(screen.getByRole('link', { name: USER.nickname }));
    await user.click(screen.getByRole('menuitem', { name: '로그아웃' }));

    await waitFor(() => {
      expect(screen.getByTestId('logout-auth-state')).toHaveTextContent('signed-out');
      expect(screen.getByTestId('logout-location')).toHaveTextContent('/signin');
    });
    expect(getAccessToken()).toBeNull();
    expect(calls.filter((call) => call === 'POST /api/v1/auth/signout')).toHaveLength(1);
  });
});
