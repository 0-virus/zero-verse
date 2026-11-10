import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import { NotificationProvider, useNotificationCenter, useNotificationPreview } from './NotificationProvider';
import { listNotifications } from './notificationApi';
import { useOptionalAuth } from '../../lib/authContext';

vi.mock('../../lib/authContext', () => ({ useOptionalAuth: vi.fn() }));
vi.mock('./notificationApi', () => ({
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

const response = (unreadCount: number, marker: string, canManage = true) => ({
  items: [
    {
      id: marker === 'preview' ? 1 : 2,
      type: 'LIKE' as const,
      message: `${marker} 알림`,
      actor: null,
      targetType: 'POST' as const,
      targetId: 42,
      href: '/blog/seven/42',
      available: true,
      readAt: null,
      createdAt: '2026-11-10T00:00:00Z',
    },
  ],
  page: 0,
  size: marker === 'preview' ? 3 : 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
  unreadCount,
  canManage,
});

function Probe() {
  const preview = useNotificationPreview();
  const center = useNotificationCenter(0, 20);
  return (
    <>
      <output data-testid="preview-message">{preview?.preview.page?.items[0]?.message ?? ''}</output>
      <output data-testid="center-message">{center?.center.page?.items[0]?.message ?? ''}</output>
      <output data-testid="unread">{preview?.unreadCount ?? 'none'}</output>
      <output data-testid="can-manage">{String(preview?.canManage ?? 'none')}</output>
    </>
  );
}

describe('NotificationProvider', () => {
  beforeEach(() => {
    vi.mocked(useOptionalAuth).mockReturnValue(auth);
    vi.mocked(listNotifications).mockImplementation(async ({ size } = {}) =>
      size === 3 ? response(3, 'preview') : response(2, 'center'),
    );
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('preview와 center page를 분리하고 server unreadCount를 공유한다', async () => {
    render(
      <NotificationProvider>
        <Probe />
      </NotificationProvider>,
    );

    expect(await screen.findByTestId('preview-message')).toHaveTextContent('preview 알림');
    await waitFor(() => expect(screen.getByTestId('center-message')).toHaveTextContent('center 알림'));
    expect(screen.getByTestId('unread')).toHaveTextContent('3');
    expect(listNotifications).toHaveBeenCalledWith({ read: 'all', page: 0, size: 3 });
    expect(listNotifications).toHaveBeenCalledWith({ read: 'all', page: 0, size: 20 });
  });

  it('인증 준비 중과 guest에서는 보호 목록을 호출하지 않는다', () => {
    vi.mocked(useOptionalAuth).mockReturnValue({ ...auth, user: null, isAuthenticated: false, isLoading: true });
    render(
      <NotificationProvider>
        <Probe />
      </NotificationProvider>,
    );

    expect(listNotifications).not.toHaveBeenCalled();
  });

  it('canManage=false 응답은 읽기 전용 상태로 공유한다', async () => {
    vi.mocked(listNotifications).mockResolvedValue(response(4, 'preview', false));
    render(
      <NotificationProvider>
        <Probe />
      </NotificationProvider>,
    );

    expect(await screen.findByTestId('can-manage')).toHaveTextContent('false');
  });

  it('visibility 복귀는 preview를 갱신하고 polling은 만들지 않는다', async () => {
    render(
      <NotificationProvider>
        <Probe />
      </NotificationProvider>,
    );
    await screen.findByText('preview 알림');
    const initial = vi.mocked(listNotifications).mock.calls.length;

    await act(async () => {
      Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' });
      document.dispatchEvent(new Event('visibilitychange'));
    });

    expect(vi.mocked(listNotifications).mock.calls.length).toBeGreaterThan(initial);
  });
});
