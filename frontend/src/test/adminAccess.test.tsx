import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom';
import { AuthProvider } from '../lib/authContext';
import { resetApiClient } from '../lib/apiClient';
import { AdminAccessProvider, useAdminAccess } from '../features/admin/AdminAccessProvider';
import { AdminRoute } from '../routes/guards';
import type { AuthUser } from '../types/auth';

const USER: AuthUser = {
  id: 7,
  email: 'user@zeroverse.test',
  name: '사용자',
  nickname: '사용자',
  role: 'USER',
  profileImageUrl: null,
  defaultBlog: { id: 1, title: '블로그', urlSlug: 'user', isSetupCompleted: true },
};

function envelope(data: unknown, success = true, error: unknown = null) {
  return {
    ok: success,
    status: success ? 200 : 403,
    text: async () => JSON.stringify({ success, data: success ? data : null, error, timestamp: '' }),
    json: async () => ({ success, data: success ? data : null, error, timestamp: '' }),
  } as unknown as Response;
}

function ProbeHarness() {
  const access = useAdminAccess();
  return (
    <div>
      <span data-testid="status">{access.status}</span>
      <button type="button" onClick={() => void access.probe()}>
        probe
      </button>
      {access.error !== null && <span role="alert">{String(access.error)}</span>}
    </div>
  );
}

function renderAccess(user: AuthUser | null, adminResponse: Response | null) {
  const fetchMock = vi.fn(async (url: string) => {
    if (url.includes('/auth/refresh')) {
      return user ? envelope({ accessToken: 't', tokenType: 'Bearer', expiresIn: 3600 }) : envelope(null, false, { code: 'AUTH_003', message: 'guest', details: [] });
    }
    if (url.includes('/auth/me')) return envelope(user);
    if (url.includes('/api/v1/admin/users')) {
      if (!adminResponse) throw new Error('network');
      return adminResponse;
    }
    return envelope(null);
  });
  vi.stubGlobal('fetch', fetchMock);
  const rendered = render(
    <MemoryRouter>
      <AuthProvider>
        <AdminAccessProvider>
          <ProbeHarness />
        </AdminAccessProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
  return { ...rendered, fetchMock };
}

function renderRoute(user: AuthUser | null, adminResponse: Response | null) {
  const fetchMock = vi.fn(async (url: string) => {
    if (url.includes('/auth/refresh')) {
      return user ? envelope({ accessToken: 't', tokenType: 'Bearer', expiresIn: 3600 }) : envelope(null, false, { code: 'AUTH_003', message: 'guest', details: [] });
    }
    if (url.includes('/auth/me')) return envelope(user);
    if (url.includes('/api/v1/admin/users')) {
      if (!adminResponse) throw new Error('network');
      return adminResponse;
    }
    return envelope(null);
  });
  vi.stubGlobal('fetch', fetchMock);
  render(
    <MemoryRouter initialEntries={['/admin']}>
      <AuthProvider>
        <AdminAccessProvider>
          <Routes>
            <Route path="/admin" element={<AdminRoute><p>관리자 민감 화면</p></AdminRoute>} />
            <Route path="/signin" element={<p>로그인 화면</p>} />
          </Routes>
        </AdminAccessProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
  return fetchMock;
}

function RouteSwitcher() {
  const navigate = useNavigate();
  return (
    <div>
      <button type="button" onClick={() => navigate('/outside')}>outside</button>
      <button type="button" onClick={() => navigate('/admin')}>admin</button>
    </div>
  );
}

describe('AdminAccessProvider', () => {
  beforeEach(() => resetApiClient());
  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it('guest 세션에서는 admin probe를 호출하지 않는다', async () => {
    const fetchMock = vi.fn(async (url: string) => {
      if (url.includes('/auth/refresh')) return envelope(null, false, { code: 'AUTH_003', message: 'guest', details: [] });
      return envelope(null);
    });
    vi.stubGlobal('fetch', fetchMock);
    renderAccess(null, null);

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('idle'));
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/api/v1/admin/users'))).toBe(false);
  });

  it('stale USER claim이어도 size=1 probe 성공이면 관리자 접근을 허용한다', async () => {
    const user = userEvent.setup();
    const response = envelope({ users: { items: [], page: 0, size: 1, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false }, summary: { totalUsers: 1, joinedThisWeek: 0, suspendedUsers: 0 } });
    const rendered = renderAccess(USER, response);
    await waitFor(() => expect(rendered.fetchMock.mock.calls.some(([url]) => String(url).includes('/auth/me'))).toBe(true));

    await user.click(screen.getByRole('button', { name: 'probe' }));
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('allowed'));
  });

  it('현재 actor가 non-admin이면 민감 화면을 허용하지 않는다', async () => {
    const user = userEvent.setup();
    const response = envelope(null, false, { code: 'ADMIN_001', message: '관리자 권한이 필요합니다.', details: [] });
    const rendered = renderAccess(USER, response);
    await waitFor(() => expect(rendered.fetchMock.mock.calls.some(([url]) => String(url).includes('/auth/me'))).toBe(true));

    await user.click(screen.getByRole('button', { name: 'probe' }));
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('denied'));
  });

  it('AdminRoute는 guest를 signin으로 보내고 admin endpoint를 호출하지 않는다', async () => {
    const fetchMock = renderRoute(null, null);

    await waitFor(() => expect(screen.getByText('로그인 화면')).toBeInTheDocument());
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/api/v1/admin/users'))).toBe(false);
  });

  it('AdminRoute는 stale USER claim을 current probe 성공으로 승격한다', async () => {
    const response = envelope({ users: { items: [], page: 0, size: 1, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false }, summary: { totalUsers: 1, joinedThisWeek: 0, suspendedUsers: 0 } });
    renderRoute(USER, response);

    await waitFor(() => expect(screen.getByText('관리자 민감 화면')).toBeInTheDocument());
  });

  it('denied 상태도 관리자 경로 재진입에서 current probe로 승격한다', async () => {
    let allowed = false;
    const fetchMock = vi.fn(async (url: string) => {
      if (url.includes('/auth/refresh')) return envelope({ accessToken: 't', tokenType: 'Bearer', expiresIn: 3600 });
      if (url.includes('/auth/me')) return envelope(USER);
      if (url.includes('/api/v1/admin/users')) {
        return allowed
          ? envelope({ users: { items: [], page: 0, size: 1, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false }, summary: { totalUsers: 1, joinedThisWeek: 0, suspendedUsers: 0 } })
          : envelope(null, false, { code: 'ADMIN_001', message: '관리자 권한이 필요합니다.', details: [] });
      }
      return envelope(null);
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <AuthProvider>
          <AdminAccessProvider>
            <RouteSwitcher />
            <Routes>
              <Route path="/admin" element={<AdminRoute><p>관리자 민감 화면</p></AdminRoute>} />
              <Route path="/outside" element={<p>outside</p>} />
            </Routes>
          </AdminAccessProvider>
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByText('관리자 화면에 접근할 수 없습니다')).toBeInTheDocument());
    allowed = true;
    await user.click(screen.getByRole('button', { name: 'outside' }));
    await user.click(screen.getByRole('button', { name: 'admin' }));
    await waitFor(() => expect(screen.getByText('관리자 민감 화면')).toBeInTheDocument());
    expect(fetchMock.mock.calls.filter(([url]) => String(url).includes('/api/v1/admin/users')).length).toBeGreaterThanOrEqual(2);
  });

  it('허용된 관리자 화면은 브라우저 focus 복귀에서 권한을 다시 확인한다', async () => {
    let allowed = true;
    const fetchMock = vi.fn(async (url: string) => {
      if (url.includes('/auth/refresh')) return envelope({ accessToken: 't', tokenType: 'Bearer', expiresIn: 3600 });
      if (url.includes('/auth/me')) return envelope(USER);
      if (url.includes('/api/v1/admin/users')) {
        return allowed
          ? envelope({ users: { items: [], page: 0, size: 1, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false }, summary: { totalUsers: 1, joinedThisWeek: 0, suspendedUsers: 0 } })
          : envelope(null, false, { code: 'ADMIN_001', message: '관리자 권한이 필요합니다.', details: [] });
      }
      return envelope(null);
    });
    vi.stubGlobal('fetch', fetchMock);
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <AuthProvider>
          <AdminAccessProvider>
            <Routes>
              <Route path="/admin" element={<AdminRoute><p>관리자 민감 화면</p></AdminRoute>} />
            </Routes>
          </AdminAccessProvider>
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByText('관리자 민감 화면')).toBeInTheDocument());
    allowed = false;
    fireEvent(window, new Event('focus'));
    await waitFor(() => expect(screen.getByText('관리자 화면에 접근할 수 없습니다')).toBeInTheDocument());
    expect(screen.queryByText('관리자 민감 화면')).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([url]) => String(url).includes('/api/v1/admin/users')).length).toBeGreaterThanOrEqual(2);
  });

  it('visible 복귀 이벤트도 허용된 관리자 화면의 current probe를 갱신한다', async () => {
    let allowed = true;
    const fetchMock = vi.fn(async (url: string) => {
      if (url.includes('/auth/refresh')) return envelope({ accessToken: 't', tokenType: 'Bearer', expiresIn: 3600 });
      if (url.includes('/auth/me')) return envelope(USER);
      if (url.includes('/api/v1/admin/users')) {
        return allowed
          ? envelope({ users: { items: [], page: 0, size: 1, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false }, summary: { totalUsers: 1, joinedThisWeek: 0, suspendedUsers: 0 } })
          : envelope(null, false, { code: 'ADMIN_001', message: '관리자 권한이 필요합니다.', details: [] });
      }
      return envelope(null);
    });
    vi.stubGlobal('fetch', fetchMock);
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <AuthProvider>
          <AdminAccessProvider>
            <Routes>
              <Route path="/admin" element={<AdminRoute><p>관리자 민감 화면</p></AdminRoute>} />
            </Routes>
          </AdminAccessProvider>
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByText('관리자 민감 화면')).toBeInTheDocument());
    allowed = false;
    fireEvent(document, new Event('visibilitychange'));
    await waitFor(() => expect(screen.getByText('관리자 화면에 접근할 수 없습니다')).toBeInTheDocument());
    expect(screen.queryByText('관리자 민감 화면')).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([url]) => String(url).includes('/api/v1/admin/users')).length).toBeGreaterThanOrEqual(2);
  });
});
