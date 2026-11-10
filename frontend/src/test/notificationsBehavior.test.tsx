import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { NotificationsPage } from '../pages/NotificationsPage';
import { NotificationProvider } from '../features/notifications/NotificationProvider';
import { listNotifications, markAllNotificationsRead, markNotificationRead, deleteNotification } from '../features/notifications/notificationApi';
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

const available = {
  id: 1,
  type: 'LIKE' as const,
  message: '궤도역학님이 좋아요를 보냈습니다.',
  actor: { id: 11, nickname: '궤도역학', profileImageUrl: null },
  targetType: 'POST' as const,
  targetId: 42,
  href: '/blog/seven/42',
  available: true,
  readAt: null,
  createdAt: '2026-11-10T00:00:00Z',
};

const unavailable = {
  id: 2,
  type: 'COMMENT' as const,
  message: '더 이상 확인할 수 없는 알림입니다',
  actor: null,
  targetType: null,
  targetId: null,
  href: null,
  available: false,
  readAt: '2026-11-09T00:00:00Z',
  createdAt: '2026-11-09T00:00:00Z',
};

function page(canManage = true) {
  return {
    items: [available, unavailable],
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
    unreadCount: 1,
    canManage,
  };
}

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}</output>;
}

function renderPage(canManage = true) {
  vi.mocked(listNotifications).mockResolvedValue(page(canManage));
  return render(
    <MemoryRouter initialEntries={['/notifications']}>
      <NotificationProvider>
        <NotificationsPage />
        <LocationProbe />
      </NotificationProvider>
    </MemoryRouter>,
  );
}

describe('NotificationsPage', () => {
  beforeEach(() => vi.mocked(useOptionalAuth).mockReturnValue(auth));
  afterEach(() => vi.clearAllMocks());

  it('전체 이력·unread·unavailable masking·전체 이력 footer를 렌더한다', async () => {
    renderPage();

    expect(await screen.findByText(available.message)).toBeInTheDocument();
    expect(screen.getByText('읽지 않음 1')).toBeInTheDocument();
    expect(screen.getByText('더 이상 확인할 수 없는 알림입니다')).toBeInTheDocument();
    expect(screen.getByText('알림을 모두 확인했어요 ✦')).toBeInTheDocument();
    expect(screen.queryByText('지난 30일')).not.toBeInTheDocument();
  });

  it('읽음 성공 응답의 현재 href로 이동하고, 삭제 실패는 행을 보존한다', async () => {
    const user = userEvent.setup();
    vi.mocked(markNotificationRead).mockResolvedValue({ ...available, href: '/blog/current/42', readAt: '2026-11-10T01:00:00Z' });
    vi.mocked(deleteNotification).mockRejectedValue(new Error('삭제 실패'));
    renderPage();

    await waitFor(() => expect(screen.getByText(available.message)).toBeInTheDocument());
    await user.click(screen.getByText(available.message));
    expect(screen.getByTestId('location')).toHaveTextContent('/blog/current/42');
    await user.click(screen.getAllByRole('button', { name: '알림 삭제' })[0]);
    expect(await screen.findByRole('alert')).toHaveTextContent('삭제');
    expect(screen.getByText(available.message)).toBeInTheDocument();
  });

  it('SUSPENDED canManage=false는 읽기 전용으로 관리 mutation을 호출하지 않는다', async () => {
    const user = userEvent.setup();
    renderPage(false);

    expect(await screen.findByText(/읽기 전용/)).toBeInTheDocument();
    const readAll = screen.getByRole('button', { name: '모두 읽음 처리' });
    expect(readAll).toBeDisabled();
    await user.click(readAll);
    expect(markAllNotificationsRead).not.toHaveBeenCalled();
    expect(markNotificationRead).not.toHaveBeenCalled();
    expect(deleteNotification).not.toHaveBeenCalled();
  });

  it('SUSPENDED 알림은 읽음 mutation 없이 서버 href만 열 수 있다', async () => {
    const user = userEvent.setup();
    renderPage(false);

    await user.click(await screen.findByText(available.message));

    expect(screen.getByTestId('location')).toHaveTextContent('/blog/seven/42');
    expect(markNotificationRead).not.toHaveBeenCalled();
  });

  it('센터 URL page를 API offset page로 복원한다', async () => {
    vi.mocked(listNotifications).mockResolvedValue({
      ...page(),
      items: [],
      page: 2,
      totalElements: 41,
      totalPages: 3,
    });
    render(
      <MemoryRouter initialEntries={['/notifications?page=2']}>
        <NotificationProvider>
          <NotificationsPage />
        </NotificationProvider>
      </MemoryRouter>,
    );

    await screen.findByText('이 페이지에는 알림이 없습니다. 다른 페이지를 선택해 주세요.');
    expect(listNotifications).toHaveBeenCalledWith({ read: 'all', page: 2, size: 20 });
  });
});
