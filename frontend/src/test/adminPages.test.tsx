import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AdminPage } from '../pages/AdminPage';
import { AdminUserPage } from '../pages/AdminUserPage';
import { adminApi } from '../features/admin/adminApi';

const summary = {
  id: 7,
  email: 'orbit@universe.dev',
  name: '궤도역학',
  nickname: '궤도역학',
  role: 'USER' as const,
  status: 'ACTIVE' as const,
  profileImageUrl: null,
  createdAt: '2026-03-02T00:00:00Z',
  updatedAt: '2026-03-02T00:00:00Z',
  defaultBlog: { id: 2, title: '궤도역학', urlSlug: 'orbit' },
  blogCount: 1,
  postCount: 31,
};

const detail = {
  ...summary,
  postStats: { total: 31, published: 30, draft: 1 },
  commentCount: 128,
  universeStats: { outgoingCount: 8, incomingCount: 9, connectionCount: 5 },
  lastAccessAt: null,
};

const access = {
  status: 'allowed' as const,
  isLoading: false,
  error: null,
  generation: 1,
  viewerId: 1,
  probe: vi.fn(async () => true),
  retry: vi.fn(async () => true),
  invalidate: vi.fn(),
  isCurrent: vi.fn(() => true),
  revalidateAfterError: vi.fn(async () => 'target' as const),
};

vi.mock('../features/admin/AdminAccessProvider', () => ({
  useAdminAccess: () => access,
  isAdminPermissionError: () => false,
  isAdminActorNotFound: () => false,
  adminAccessErrorMessage: (error: unknown) => error instanceof Error ? error.message : '관리자 오류',
}));

vi.mock('../features/admin/adminApi', () => ({
  DEFAULT_ADMIN_PAGE_SIZE: 20,
  normalizeAdminKeyword: (value: string | undefined) => (value ?? '').trim(),
  adminApi: {
    list: vi.fn(async () => ({ users: { items: [summary], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false }, summary: { totalUsers: 1, joinedThisWeek: 0, suspendedUsers: 0 } })),
    get: vi.fn(async () => detail),
    changeRole: vi.fn(async () => detail),
    changeStatus: vi.fn(async () => detail),
    delete: vi.fn(async () => null),
  },
}));

describe('관리자 화면', () => {
  beforeEach(() => vi.clearAllMocks());
  afterEach(() => vi.restoreAllMocks());

  it('목록은 검색·통계·관리 액션과 디자인 카피를 렌더한다', async () => {
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route path="/admin" element={<AdminPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByText('전체 사용자')).toBeInTheDocument());
    expect(screen.getByText('이번 주 가입')).toBeInTheDocument();
    expect(screen.getByText('정지 계정')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('이메일 · 닉네임 검색')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '관리' })).toHaveAttribute('href', '/admin/users/7');
    const table = screen.getByRole('table', { name: '관리자 사용자 목록' });
    expect(table).toHaveAttribute('data-admin-user-grid', '2fr 1.6fr 1fr 1fr 1fr 90px');
    expect(table.querySelector('thead tr')).toHaveClass('gap-[10px]', 'px-5', 'py-[11px]', 'text-[11px]');
    expect(table.querySelector('tbody tr')).toHaveClass('gap-[10px]', 'px-5', 'py-[13px]');
  });

  it('상세는 lastAccess 미기록을 표시하고 역할·상태·삭제 확인을 제공한다', async () => {
    render(
      <MemoryRouter initialEntries={['/admin/users/7']}>
        <Routes>
          <Route path="/admin/users/:userId" element={<AdminUserPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('heading', { name: '사용자 상세' })).toBeInTheDocument());
    expect(screen.getByText(/마지막 접속 기록 없음/u)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '역할 변경' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '일시 정지' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '계정 삭제' })).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '계정 삭제' }));
    expect(screen.getAllByText('계정 접근이 차단되고 블로그와 게시글이 공개되지 않습니다. 데이터는 보존됩니다.').length).toBeGreaterThanOrEqual(2);
  });

  it('상태 변경은 canonical 상세를 다시 읽고 삭제 확인은 승인 카피 뒤 delete를 호출한다', async () => {
    render(
      <MemoryRouter initialEntries={['/admin/users/7']}>
        <Routes>
          <Route path="/admin/users/:userId" element={<AdminUserPage />} />
        </Routes>
      </MemoryRouter>,
    );
    const user = userEvent.setup();
    await waitFor(() => expect(screen.getByRole('heading', { name: '사용자 상세' })).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: '일시 정지' }));
    await user.click(screen.getByRole('button', { name: '변경 확인' }));
    await waitFor(() => expect(vi.mocked(adminApi.changeStatus)).toHaveBeenCalledWith(7, 'SUSPENDED'));
    expect(vi.mocked(adminApi.get).mock.calls.length).toBeGreaterThanOrEqual(2);

    await user.click(screen.getByRole('button', { name: '계정 삭제' }));
    await user.click(screen.getByRole('button', { name: '삭제 확인' }));
    await waitFor(() => expect(vi.mocked(adminApi.delete)).toHaveBeenCalledWith(7));
  });

  it('transient 목록 오류에서도 직전 사용자 행과 pagination을 보존한다', async () => {
    vi.mocked(adminApi.list)
      .mockResolvedValueOnce({
        users: { items: [summary], page: 0, size: 20, totalElements: 21, totalPages: 2, hasNext: true, hasPrevious: false },
        summary: { totalUsers: 21, joinedThisWeek: 2, suspendedUsers: 1 },
      })
      .mockRejectedValueOnce(new Error('temporary list failure'));

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route path="/admin" element={<AdminPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByText('orbit@universe.dev')).toBeInTheDocument());
    expect(screen.getByRole('navigation', { name: '페이지' })).toBeInTheDocument();
    const user = userEvent.setup();
    const search = screen.getByPlaceholderText('이메일 · 닉네임 검색');
    await user.type(search, 'next');
    await user.keyboard('{Enter}');

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('temporary list failure'));
    expect(screen.getByText('orbit@universe.dev')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: '페이지' })).toBeInTheDocument();
  });
});
