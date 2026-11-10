import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { TopBar } from '../components/layout/TopBar';
import { RightPanel } from '../components/layout/RightPanel';
import { NotificationProvider } from '../features/notifications/NotificationProvider';
import { listNotifications, markNotificationRead } from '../features/notifications/notificationApi';
import { useOptionalAuth } from '../lib/authContext';

vi.mock('../lib/authContext', () => ({ useOptionalAuth: vi.fn() }));
vi.mock('../features/notifications/notificationApi', () => ({
  listNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
  deleteNotification: vi.fn(),
}));

const auth = {
  user: {
    id: 7,
    email: 'seven@zeroverse.test',
    name: '세븐',
    nickname: '세븐별',
    role: 'USER' as const,
    profileImageUrl: null,
    defaultBlog: { id: 17, title: '세븐별', urlSlug: 'seven', isSetupCompleted: true },
  },
  isLoading: false,
  isAuthenticated: true,
  sessionExpired: false,
  signin: vi.fn(),
  register: vi.fn(),
  signout: vi.fn(),
  refreshUser: vi.fn(),
};

const item = {
  id: 1,
  type: 'POST' as const,
  message: '새 글 신호가 도착했습니다.',
  actor: null,
  targetType: 'POST' as const,
  targetId: 42,
  href: '/blog/seven/42',
  available: true,
  readAt: null,
  createdAt: '2026-11-10T00:00:00Z',
};

const page = {
  items: [item],
  page: 0,
  size: 3,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
  unreadCount: 1,
  canManage: true,
};

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="shell-location">{location.pathname}</output>;
}

describe('notification shell UI', () => {
  beforeEach(() => {
    vi.mocked(useOptionalAuth).mockReturnValue(auth);
    vi.mocked(listNotifications).mockResolvedValue(page);
    vi.mocked(markNotificationRead).mockResolvedValue({ ...item, href: '/blog/current/42', readAt: '2026-11-10T01:00:00Z' });
  });

  afterEach(() => vi.clearAllMocks());

  it('TopBar dropdown은 열기만으로 읽지 않고 최신 preview를 표시한다', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <NotificationProvider>
          <TopBar nickname="세븐별" />
        </NotificationProvider>
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('link', { name: '알림' }));
    expect(await screen.findByRole('dialog', { name: '최근 알림' })).toBeInTheDocument();
    expect(screen.getByText(item.message)).toBeInTheDocument();
    expect(markNotificationRead).not.toHaveBeenCalled();
    expect(listNotifications).toHaveBeenCalledWith({ read: 'all', page: 0, size: 3 });
  });

  it('TopBar preview 클릭은 read 응답의 canonical href로 이동한다', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <NotificationProvider>
          <TopBar nickname="세븐별" />
          <LocationProbe />
        </NotificationProvider>
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('link', { name: '알림' }));
    await user.click(await screen.findByRole('button', { name: item.message }));
    expect(screen.getByTestId('shell-location')).toHaveTextContent('/blog/current/42');
  });

  it('TopBar read 실패는 dropdown에서 같은 mutation을 다시 시도한다', async () => {
    const user = userEvent.setup();
    vi.mocked(markNotificationRead)
      .mockRejectedValueOnce(new Error('읽음 실패'))
      .mockResolvedValueOnce({ ...item, href: '/blog/retried/42', readAt: '2026-11-10T01:00:00Z' });
    render(
      <MemoryRouter>
        <NotificationProvider>
          <TopBar nickname="세븐별" />
          <LocationProbe />
        </NotificationProvider>
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('link', { name: '알림' }));
    await user.click(await screen.findByRole('button', { name: item.message }));
    expect(await screen.findByRole('alert')).toHaveTextContent('읽음 실패');
    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    await screen.findByText('알림');
    expect(screen.getByTestId('shell-location')).toHaveTextContent('/blog/retried/42');
    expect(markNotificationRead).toHaveBeenCalledTimes(2);
  });

  it('TopBar A 계정의 old retry는 B 계정 전환 시 제거된다', async () => {
    const user = userEvent.setup();
    const view = render(
      <MemoryRouter>
        <NotificationProvider>
          <TopBar nickname="세븐별" />
        </NotificationProvider>
      </MemoryRouter>,
    );
    vi.mocked(markNotificationRead).mockRejectedValueOnce(new Error('A 읽음 실패'));

    await user.click(screen.getByRole('link', { name: '알림' }));
    await user.click(await screen.findByRole('button', { name: item.message }));
    expect(await screen.findByRole('button', { name: '다시 시도' })).toBeInTheDocument();

    vi.mocked(useOptionalAuth).mockReturnValue({
      ...auth,
      user: { ...auth.user, id: 8, nickname: '여덟별' },
    });
    view.rerender(
      <MemoryRouter>
        <NotificationProvider>
          <TopBar nickname="여덟별" />
        </NotificationProvider>
      </MemoryRouter>,
    );
    await waitFor(() => expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument());
    expect(markNotificationRead).toHaveBeenCalledTimes(1);
  });

  it('RightPanel은 공유 unread와 preview 최신 3건·센터 링크를 표시한다', async () => {
    render(
      <MemoryRouter>
        <NotificationProvider>
          <RightPanel />
        </NotificationProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByText(item.message)).toBeInTheDocument();
    expect(screen.getByText('읽지 않음 1')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '알림 센터 열기 →' })).toHaveAttribute('href', '/notifications');
  });

  it('RightPanel read 실패는 같은 mutation을 다시 시도한다', async () => {
    const user = userEvent.setup();
    vi.mocked(markNotificationRead)
      .mockRejectedValueOnce(new Error('읽음 실패'))
      .mockResolvedValueOnce({ ...item, href: '/blog/retried/42', readAt: '2026-11-10T01:00:00Z' });
    render(
      <MemoryRouter>
        <NotificationProvider>
          <RightPanel />
          <LocationProbe />
        </NotificationProvider>
      </MemoryRouter>,
    );

    await user.click(await screen.findByRole('button', { name: item.message }));
    expect(await screen.findByRole('alert')).toHaveTextContent('읽음 실패');
    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    expect(screen.getByTestId('shell-location')).toHaveTextContent('/blog/retried/42');
    expect(markNotificationRead).toHaveBeenCalledTimes(2);
  });
});

