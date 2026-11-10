import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { NotificationsPage } from '../pages/NotificationsPage';
import { NotificationProvider, useNotificationCenter, useOptionalNotifications } from '../features/notifications/NotificationProvider';
import { listNotifications, markAllNotificationsRead, markNotificationRead, deleteNotification } from '../features/notifications/notificationApi';
import { useOptionalAuth } from '../lib/authContext';
import { ApiRequestError } from '../lib/apiClient';

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

function CenterRefreshProbe() {
  const context = useNotificationCenter(0, 20);
  return (
    <button type="button" onClick={() => void context?.refreshCenter(0, 20)}>
      refresh-center
    </button>
  );
}

function PreviewAuthFailureProbe() {
  const context = useOptionalNotifications();
  return (
    <>
      <output data-testid="preview-auth-failure">{String(context?.isAuthFailure ?? false)}</output>
      <button type="button" onClick={() => void context?.refreshPreview()}>trigger-preview</button>
    </>
  );
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

  it('읽음 실패의 다시 시도는 같은 read mutation을 재호출하고 canonical href로 이동한다', async () => {
    const user = userEvent.setup();
    vi.mocked(markNotificationRead)
      .mockRejectedValueOnce(new Error('읽음 실패'))
      .mockResolvedValueOnce({ ...available, href: '/blog/retried/42', readAt: '2026-11-10T01:00:00Z' });
    renderPage();

    await user.click(await screen.findByText(available.message));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('읽음 실패');
    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/blog/retried/42'));
    expect(markNotificationRead).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('동일 center query의 transient GET 실패는 기존 행과 pagination을 보존한다', async () => {
    const user = userEvent.setup();
    const centerPage = { ...page(), totalElements: 41, totalPages: 3, hasNext: true };
    let centerRequests = 0;
    vi.mocked(listNotifications).mockImplementation(async ({ size } = {}) => {
      if (size === 3) return { ...centerPage, size: 3 };
      centerRequests += 1;
      if (centerRequests === 1) return centerPage;
      throw new Error('일시 오류');
    });
    render(
      <MemoryRouter initialEntries={['/notifications']}>
        <NotificationProvider>
          <NotificationsPage />
          <CenterRefreshProbe />
        </NotificationProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByText(available.message)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'refresh-center' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('일시 오류');
    expect(screen.getByText(available.message)).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: '페이지' })).toBeInTheDocument();
  });

  it('A 계정 read-all 실패 retry는 계정 전환 시 제거되어 B API를 호출하지 않는다', async () => {
    const user = userEvent.setup();
    const view = renderPage();
    vi.mocked(markAllNotificationsRead).mockRejectedValueOnce(new Error('A read-all 실패'));
    await user.click(await screen.findByRole('button', { name: '모두 읽음 처리' }));
    expect(await screen.findByRole('button', { name: '다시 시도' })).toBeInTheDocument();

    vi.mocked(useOptionalAuth).mockReturnValue({
      ...auth,
      user: { ...auth.user, id: 8, nickname: '여덟별' },
    });
    view.rerender(
      <MemoryRouter initialEntries={['/notifications']}>
        <NotificationProvider>
          <NotificationsPage />
          <LocationProbe />
        </NotificationProvider>
      </MemoryRouter>,
    );
    await waitFor(() => expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument());
    expect(markAllNotificationsRead).toHaveBeenCalledTimes(1);
  });

  it('transient read-all 실패 뒤 USER_001이면 이전 retry를 다시 호출하지 않는다', async () => {
    const user = userEvent.setup();
    let centerCalls = 0;
    vi.mocked(listNotifications).mockImplementation(async ({ size } = {}) => {
      if (size === 3) {
        throw new ApiRequestError(404, { code: 'USER_001', message: '사용자를 찾을 수 없습니다.', details: [] });
      }
      centerCalls += 1;
      return page();
    });
    render(
      <MemoryRouter initialEntries={['/notifications']}>
        <NotificationProvider>
          <NotificationsPage />
          <PreviewAuthFailureProbe />
        </NotificationProvider>
      </MemoryRouter>,
    );
    await waitFor(() => expect(screen.getByText(available.message)).toBeInTheDocument());
    vi.mocked(markAllNotificationsRead).mockRejectedValueOnce(new Error('일시 read-all 실패'));
    await user.click(screen.getByRole('button', { name: '모두 읽음 처리' }));
    expect(await screen.findByRole('button', { name: '다시 시도' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'trigger-preview' }));
    await waitFor(() => expect(screen.getByTestId('preview-auth-failure')).toHaveTextContent('true'));
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument();
    expect(markAllNotificationsRead).toHaveBeenCalledTimes(1);
    expect(centerCalls).toBeGreaterThan(0);
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
