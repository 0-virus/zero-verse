import { describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { TopBar } from '../components/layout/TopBar';
import { useOptionalAuth } from '../lib/authContext';

vi.mock('../lib/authContext', () => ({ useOptionalAuth: vi.fn() }));

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="topbar-location">{location.pathname}</output>;
}

describe('TopBar', () => {
  const setup = () =>
    render(
      <MemoryRouter>
        <TopBar />
      </MemoryRouter>,
    );

  it('로고·검색·글쓰기·알림·프로필을 렌더한다', () => {
    setup();
    expect(screen.getByRole('link', { name: 'ZEROVERSE' })).toBeInTheDocument();
    expect(screen.getByRole('searchbox', { name: '검색' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '✎ 글쓰기' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '알림' })).toBeInTheDocument();
    const profileLink = screen.getByRole('link', { name: '로그인' });
    expect(profileLink).toHaveAttribute('href', '/signin');
    expect(screen.queryByRole('menuitem', { name: '로그아웃' })).not.toBeInTheDocument();
  });

  it('관리자 항목을 노출하지 않는다 (PRD §9-K 폐기)', () => {
    setup();
    expect(screen.queryByText(/관리자/)).not.toBeInTheDocument();
    expect(screen.queryByText(/ADMIN/i)).not.toBeInTheDocument();
  });

  it('인증된 프로필 메뉴에서 로그아웃하고 로그인 화면으로 이동한다', async () => {
    const user = userEvent.setup();
    const signout = vi.fn().mockResolvedValue(undefined);
    vi.mocked(useOptionalAuth).mockReturnValue({
      user: null,
      isLoading: false,
      isAuthenticated: true,
      sessionExpired: false,
      signin: vi.fn(),
      register: vi.fn(),
      signout,
      refreshUser: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/settings']}>
        <TopBar nickname="세븐별" />
        <LocationProbe />
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('link', { name: '세븐별' }));
    await user.click(screen.getByRole('menuitem', { name: '로그아웃' }));

    expect(signout).toHaveBeenCalledTimes(1);
    expect(await screen.findByTestId('topbar-location')).toHaveTextContent('/signin');
  });

  it('로그아웃 요청 중에는 중복 요청을 막는다', async () => {
    const user = userEvent.setup();
    let resolveSignout!: () => void;
    const signout = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolveSignout = resolve;
        }),
    );
    vi.mocked(useOptionalAuth).mockReturnValue({
      user: null,
      isLoading: false,
      isAuthenticated: true,
      sessionExpired: false,
      signin: vi.fn(),
      register: vi.fn(),
      signout,
      refreshUser: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/settings']}>
        <TopBar nickname="세븐별" />
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('link', { name: '세븐별' }));
    const logout = screen.getByRole('menuitem', { name: '로그아웃' });
    await user.click(logout);
    expect(logout).toBeDisabled();
    await user.click(logout);
    expect(signout).toHaveBeenCalledTimes(1);

    await act(async () => {
      resolveSignout();
    });
    expect(screen.queryByRole('menu', { name: '프로필 메뉴' })).not.toBeInTheDocument();
  });

  it('로그아웃 서버 요청이 실패해도 로그인 화면으로 돌아간다', async () => {
    const user = userEvent.setup();
    const signout = vi.fn().mockRejectedValue(new Error('네트워크 오류'));
    vi.mocked(useOptionalAuth).mockReturnValue({
      user: null,
      isLoading: false,
      isAuthenticated: true,
      sessionExpired: false,
      signin: vi.fn(),
      register: vi.fn(),
      signout,
      refreshUser: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/settings']}>
        <TopBar nickname="세븐별" />
        <LocationProbe />
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('link', { name: '세븐별' }));
    await user.click(screen.getByRole('menuitem', { name: '로그아웃' }));

    expect(signout).toHaveBeenCalledTimes(1);
    expect(await screen.findByTestId('topbar-location')).toHaveTextContent('/signin');
  });
});
