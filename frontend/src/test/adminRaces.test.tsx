import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { isAdminPermissionError, isAdminTargetNotFound } from '../features/admin/AdminAccessProvider';
import { AdminAccessProvider, useAdminAccess } from '../features/admin/AdminAccessProvider';
import * as adminAccessModule from '../features/admin/AdminAccessProvider';
import { AdminRoute } from '../routes/guards';
import { AdminPage } from '../pages/AdminPage';
import { AdminUserPage } from '../pages/AdminUserPage';
import { adminApi } from '../features/admin/adminApi';
import { ApiRequestError } from '../lib/apiClient';
import { AuthProvider, useAuth } from '../lib/authContext';
import type { AuthUser } from '../types/auth';
import { resetApiClient } from '../lib/apiClient';

function apiError(code: string, status = code === 'ADMIN_001' ? 403 : 404) {
  return new ApiRequestError(status, { code, message: code, details: [] });
}

describe('관리자 오류 경계', () => {
  it('actor 권한 오류와 target USER_001을 구분할 수 있다', () => {
    expect(isAdminPermissionError(apiError('ADMIN_001', 403))).toBe(true);
    expect(isAdminPermissionError(apiError('USER_003', 403))).toBe(true);
    expect(isAdminTargetNotFound(apiError('USER_001'))).toBe(true);
    expect(isAdminPermissionError(apiError('USER_001'))).toBe(false);
  });
});

const USERS: Record<'a' | 'b', AuthUser> = {
  a: {
    id: 11,
    email: 'a@zeroverse.test',
    name: 'A',
    nickname: 'A',
    role: 'ADMIN',
    profileImageUrl: null,
    defaultBlog: { id: 1, title: 'A', urlSlug: 'a', isSetupCompleted: true },
  },
  b: {
    id: 12,
    email: 'b@zeroverse.test',
    name: 'B',
    nickname: 'B',
    role: 'ADMIN',
    profileImageUrl: null,
    defaultBlog: { id: 2, title: 'B', urlSlug: 'b', isSetupCompleted: true },
  },
};

function response(data: unknown, success = true, error: unknown = null, statusOverride?: number) {
  return {
    ok: success,
    status: statusOverride ?? (success ? 200 : 401),
    text: async () => JSON.stringify({ success, data: success ? data : null, error, timestamp: '' }),
    json: async () => ({ success, data: success ? data : null, error, timestamp: '' }),
  } as unknown as Response;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((nextResolve) => {
    resolve = nextResolve;
  });
  return { promise, resolve };
}

function RaceHarness() {
  const auth = useAuth();
  const access = useAdminAccess();
  return (
    <div>
      <span data-testid="viewer">{auth.user?.id ?? 'guest'}</span>
      <span data-testid="access-status">{access.status}</span>
      <button type="button" onClick={() => void auth.signin({ email: 'a@zeroverse.test', password: 'pw' })}>signin-a</button>
      <button type="button" onClick={() => void auth.signin({ email: 'b@zeroverse.test', password: 'pw' })}>signin-b</button>
      <button type="button" onClick={() => void access.probe()}>probe</button>
    </div>
  );
}

describe('관리자 계정 세대 race', () => {
  beforeEach(() => resetApiClient());
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    resetApiClient();
  });

  it('A probe의 늦은 성공은 B 계정 권한으로 승격시키지 않는다', async () => {
    const user = userEvent.setup();
    const probeA = deferred<Response>();
    const probeB = deferred<Response>();
    let active: 'a' | 'b' | null = null;
    const fetchMock = vi.fn(async (url: string, options?: RequestInit) => {
      if (url.includes('/auth/refresh')) return response(null, false, { code: 'AUTH_003', message: 'guest', details: [] });
      if (url.includes('/auth/signin')) {
        active = String(options?.body).includes('a@zeroverse.test') ? 'a' : 'b';
        return response({ accessToken: `${active}-token`, tokenType: 'Bearer', expiresIn: 3600 });
      }
      if (url.includes('/auth/me')) return response(active ? USERS[active] : null);
      if (url.includes('/api/v1/admin/users')) return active === 'a' ? probeA.promise : probeB.promise;
      return response(null);
    });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <MemoryRouter>
        <AuthProvider>
          <AdminAccessProvider>
            <RaceHarness />
          </AdminAccessProvider>
        </AuthProvider>
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('button', { name: 'signin-a' }));
    await waitFor(() => expect(screen.getByTestId('viewer')).toHaveTextContent('11'));
    await user.click(screen.getByRole('button', { name: 'probe' }));
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/api/v1/admin/users'))).toBe(true));

    await user.click(screen.getByRole('button', { name: 'signin-b' }));
    await waitFor(() => expect(screen.getByTestId('viewer')).toHaveTextContent('12'));
    probeA.resolve(response({ users: { items: [], page: 0, size: 1, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false }, summary: { totalUsers: 1, joinedThisWeek: 0, suspendedUsers: 0 } }));
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(screen.getByTestId('access-status')).not.toHaveTextContent('allowed');

    await user.click(screen.getByRole('button', { name: 'probe' }));
    probeB.resolve(response({ users: { items: [], page: 0, size: 1, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false }, summary: { totalUsers: 1, joinedThisWeek: 0, suspendedUsers: 0 } }));
    await waitFor(() => expect(screen.getByTestId('access-status')).toHaveTextContent('allowed'));
  });

  it('동일 계정의 역순 검색 응답은 최신 URL 결과만 화면에 반영한다', async () => {
    const user = userEvent.setup();
    const first = deferred<Awaited<ReturnType<typeof adminApi.list>>>();
    const second = deferred<Awaited<ReturnType<typeof adminApi.list>>>();
    const listSpy = vi.spyOn(adminApi, 'list')
      .mockImplementationOnce(async () => first.promise)
      .mockImplementationOnce(async () => second.promise);
    vi.spyOn(adminAccessModule, 'useAdminAccess').mockReturnValue({
      status: 'allowed',
      isLoading: false,
      error: null,
      generation: 1,
      viewerId: 1,
      probe: async () => true,
      retry: async () => true,
      invalidate: () => undefined,
      isCurrent: () => true,
      revalidateAfterError: async () => 'transient',
    });

    render(
      <MemoryRouter initialEntries={['/admin?keyword=first']}>
        <AdminPage />
      </MemoryRouter>,
    );
    await waitFor(() => expect(listSpy).toHaveBeenCalledTimes(1));
    const search = screen.getByPlaceholderText('이메일 · 닉네임 검색');
    await user.clear(search);
    await user.type(search, 'second');
    await user.keyboard('{Enter}');
    await waitFor(() => expect(listSpy).toHaveBeenCalledTimes(2));

    const result = (label: string) => ({
      users: {
        items: [{
          id: label === 'A' ? 21 : 22,
          email: `${label}@zeroverse.test`,
          name: label,
          nickname: label,
          role: 'USER' as const,
          status: 'ACTIVE' as const,
          profileImageUrl: null,
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-01T00:00:00Z',
          defaultBlog: null,
          blogCount: 0,
          postCount: 0,
        }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      },
      summary: { totalUsers: 1, joinedThisWeek: 0, suspendedUsers: 0 },
    });
    second.resolve(result('B'));
    await waitFor(() => expect(screen.getByText('B@zeroverse.test')).toBeInTheDocument());
    first.resolve(result('A'));
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(screen.getByText('B@zeroverse.test')).toBeInTheDocument();
    expect(screen.queryByText('A@zeroverse.test')).not.toBeInTheDocument();
  });

  it('상세 GET의 역순 응답은 최신 target 상세만 유지한다', async () => {
    const user = userEvent.setup();
    const first = deferred<Awaited<ReturnType<typeof adminApi.get>>>();
    const second = deferred<Awaited<ReturnType<typeof adminApi.get>>>();
    const getSpy = vi.spyOn(adminApi, 'get')
      .mockImplementationOnce(async () => first.promise)
      .mockImplementationOnce(async () => second.promise);
    vi.spyOn(adminAccessModule, 'useAdminAccess').mockReturnValue({
      status: 'allowed',
      isLoading: false,
      error: null,
      generation: 1,
      viewerId: 1,
      probe: async () => true,
      retry: async () => true,
      invalidate: () => undefined,
      isCurrent: () => true,
      revalidateAfterError: async () => 'target',
    });
    const detail = (id: number) => ({
      id,
      email: `target${id}@zeroverse.test`,
      name: `Target ${id}`,
      nickname: `Target ${id}`,
      role: 'USER' as const,
      status: 'ACTIVE' as const,
      profileImageUrl: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      defaultBlog: null,
      blogCount: 0,
      postCount: 0,
      postStats: { total: 0, published: 0, draft: 0 },
      commentCount: 0,
      universeStats: { outgoingCount: 0, incomingCount: 0, connectionCount: 0 },
      lastAccessAt: null,
    });

    render(
      <MemoryRouter initialEntries={['/admin/users/7']}>
        <Link to="/admin/users/8">switch</Link>
        <Routes>
          <Route path="/admin/users/:userId" element={<AdminUserPage />} />
        </Routes>
      </MemoryRouter>,
    );
    await waitFor(() => expect(getSpy).toHaveBeenCalledWith(7));
    await user.click(screen.getByRole('link', { name: 'switch' }));
    await waitFor(() => expect(getSpy).toHaveBeenCalledWith(8));
    second.resolve(detail(8));
    await waitFor(() => expect(screen.getByText('target8@zeroverse.test', { exact: false })).toBeInTheDocument());
    first.resolve(detail(7));
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(screen.getByText('target8@zeroverse.test', { exact: false })).toBeInTheDocument();
    expect(screen.queryByText('target7@zeroverse.test', { exact: false })).not.toBeInTheDocument();
  });
});

function adminListResponse() {
  return {
    users: { items: [], page: 0, size: 1, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false },
    summary: { totalUsers: 1, joinedThisWeek: 0, suspendedUsers: 0 },
  };
}

function adminDetailResponse(id: number) {
  return {
    id,
    email: `target${id}@zeroverse.test`,
    name: `Target ${id}`,
    nickname: `Target ${id}`,
    role: 'USER' as const,
    status: 'ACTIVE' as const,
    profileImageUrl: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    defaultBlog: null,
    blogCount: 0,
    postCount: 0,
    postStats: { total: 0, published: 0, draft: 0 },
    commentCount: 0,
    universeStats: { outgoingCount: 0, incomingCount: 0, connectionCount: 0 },
    lastAccessAt: null,
  };
}

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="admin-path">{location.pathname}</span>;
}


describe('AdminRoute와 상세 target/actor 경계', () => {
  beforeEach(() => resetApiClient());
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    resetApiClient();
  });

  it('target USER_001은 current probe 성공 뒤 유한하게 target 404로 정착한다', async () => {
    let listCalls = 0;
    let detailCalls = 0;
    const secondProbe = deferred<Response>();
    const fetchMock = vi.fn(async (url: string) => {
      if (url.includes('/auth/refresh')) return response({ accessToken: 't', tokenType: 'Bearer', expiresIn: 3600 });
      if (url.includes('/auth/me')) return response(USERS.a);
      if (url.includes('/api/v1/admin/users/7')) {
        detailCalls += 1;
        return response(null, false, { code: 'USER_001', message: 'target missing', details: [] }, 404);
      }
      if (url.includes('/api/v1/admin/users')) {
        listCalls += 1;
        if (listCalls === 2) return secondProbe.promise;
        return response(adminListResponse());
      }
      return response(null);
    });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <MemoryRouter initialEntries={['/admin/users/7']}>
        <AuthProvider>
          <AdminAccessProvider>
            <LocationProbe />
            <Routes>
              <Route path="/admin/users/:userId" element={<AdminRoute><AdminUserPage /></AdminRoute>} />
            </Routes>
          </AdminAccessProvider>
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(listCalls).toBe(2));
    secondProbe.resolve(response(adminListResponse()));
    await waitFor(() => expect(screen.getByText('사용자를 찾을 수 없습니다')).toBeInTheDocument());
    expect(listCalls).toBe(2);
    expect(detailCalls).toBe(1);
  });

  it('actor USER_001은 probe 실패 뒤 민감 상세와 target retry를 제거한다', async () => {
    let listCalls = 0;
    let detailCalls = 0;
    const fetchMock = vi.fn(async (url: string) => {
      if (url.includes('/auth/refresh')) return response({ accessToken: 't', tokenType: 'Bearer', expiresIn: 3600 });
      if (url.includes('/auth/me')) return response(USERS.a);
      if (url.includes('/api/v1/admin/users/7')) {
        detailCalls += 1;
        return response(null, false, { code: 'USER_001', message: 'actor or target missing', details: [] }, 404);
      }
      if (url.includes('/api/v1/admin/users')) {
        listCalls += 1;
        if (listCalls > 1) return response(null, false, { code: 'USER_001', message: 'actor missing', details: [] }, 404);
        return response(adminListResponse());
      }
      return response(null);
    });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <MemoryRouter initialEntries={['/admin/users/7']}>
        <AuthProvider>
          <AdminAccessProvider>
            <Routes>
              <Route path="/admin/users/:userId" element={<AdminRoute><AdminUserPage /></AdminRoute>} />
            </Routes>
          </AdminAccessProvider>
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByText('관리자 화면에 접근할 수 없습니다')).toBeInTheDocument());
    expect(listCalls).toBe(2);
    expect(detailCalls).toBe(1);
    expect(screen.queryByText('사용자를 찾을 수 없습니다')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument();
  });
});

describe('상세 mutation target 세대', () => {
  beforeEach(() => resetApiClient());
  afterEach(() => {
    vi.restoreAllMocks();
    resetApiClient();
  });

  function mockAllowedAccess() {
    vi.spyOn(adminAccessModule, 'useAdminAccess').mockReturnValue({
      status: 'allowed',
      isLoading: false,
      error: null,
      generation: 1,
      viewerId: 1,
      probe: async () => true,
      retry: async () => true,
      invalidate: () => undefined,
      isCurrent: () => true,
      revalidateAfterError: async () => 'target',
    });
  }

  it('target7 mutation이 target8 전환 뒤 canonical 상세를 오염시키지 않는다', async () => {
    const user = userEvent.setup();
    const statusDeferred = deferred<Awaited<ReturnType<typeof adminApi.changeStatus>>>();
    const getSpy = vi.spyOn(adminApi, 'get').mockImplementation(async (id) => adminDetailResponse(id));
    const statusSpy = vi.spyOn(adminApi, 'changeStatus').mockImplementation(async () => statusDeferred.promise);
    mockAllowedAccess();

    render(
      <MemoryRouter initialEntries={['/admin/users/7']}>
        <Link to="/admin/users/8">switch</Link>
        <LocationProbe />
        <Routes>
          <Route path="/admin/users/:userId" element={<AdminUserPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByText('target7@zeroverse.test', { exact: false })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '일시 정지' }));
    await user.click(screen.getByRole('button', { name: '변경 확인' }));
    await waitFor(() => expect(statusSpy).toHaveBeenCalledWith(7, 'SUSPENDED'));

    fireEvent.click(screen.getByRole('link', { name: 'switch' }));
    await waitFor(() => expect(screen.getByText('target8@zeroverse.test', { exact: false })).toBeInTheDocument());
    statusDeferred.resolve(adminDetailResponse(7));
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(getSpy.mock.calls.filter(([id]) => id === 7)).toHaveLength(1);
    expect(screen.getByText('target8@zeroverse.test', { exact: false })).toBeInTheDocument();
    expect(screen.queryByText('target7@zeroverse.test', { exact: false })).not.toBeInTheDocument();
  });

  it('target7 삭제가 target8로 이탈한 뒤 /admin으로 강제 이동하지 않는다', async () => {
    const user = userEvent.setup();
    const deleteDeferred = deferred<null>();
    const deleteSpy = vi.spyOn(adminApi, 'delete').mockImplementation(async () => deleteDeferred.promise);
    vi.spyOn(adminApi, 'get').mockImplementation(async (id) => adminDetailResponse(id));
    mockAllowedAccess();

    render(
      <MemoryRouter initialEntries={['/admin/users/7']}>
        <Link to="/admin/users/8">switch</Link>
        <LocationProbe />
        <Routes>
          <Route path="/admin/users/:userId" element={<AdminUserPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByText('target7@zeroverse.test', { exact: false })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: '계정 삭제' }));
    await user.click(screen.getByRole('button', { name: '삭제 확인' }));
    await waitFor(() => expect(deleteSpy).toHaveBeenCalledWith(7));

    fireEvent.click(screen.getByRole('link', { name: 'switch' }));
    await waitFor(() => expect(screen.getByText('target8@zeroverse.test', { exact: false })).toBeInTheDocument());
    deleteDeferred.resolve(null);
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(screen.getByTestId('admin-path')).toHaveTextContent('/admin/users/8');
  });
});
