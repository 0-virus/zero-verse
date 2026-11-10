import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NotificationProvider, useNotificationCenter, useNotificationPreview } from './NotificationProvider';
import { listNotifications } from './notificationApi';
import { useOptionalAuth } from '../../lib/authContext';
import { ApiRequestError } from '../../lib/apiClient';

vi.mock('../../lib/authContext', () => ({ useOptionalAuth: vi.fn() }));
vi.mock('./notificationApi', () => ({
  listNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
  deleteNotification: vi.fn(),
}));

const baseAuth = {
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

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

const page = (id: number) => ({
  items: [{
    id,
    type: 'POST' as const,
    message: `계정 ${id}`,
    actor: null,
    targetType: 'POST' as const,
    targetId: id,
    href: `/blog/${id}`,
    available: true,
    readAt: null,
    createdAt: '2026-11-10T00:00:00Z',
  }],
  page: 0,
  size: 3,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
  unreadCount: id,
  canManage: true,
});

function Probe() {
  const context = useNotificationPreview();
  return <output data-testid="message">{context?.preview.page?.items[0]?.message ?? ''}</output>;
}

function AuthFailureProbe() {
  const preview = useNotificationPreview();
  const center = useNotificationCenter(0, 20);
  return (
    <>
      <output data-testid="preview-message">{preview?.preview.page?.items[0]?.message ?? ''}</output>
      <output data-testid="center-message">{center?.center.page?.items[0]?.message ?? ''}</output>
      <output data-testid="unread">{preview?.unreadCount ?? 'none'}</output>
      <output data-testid="can-manage">{String(preview?.canManage ?? 'none')}</output>
      <button type="button" onClick={() => void preview?.refreshPreview()}>refresh-preview</button>
    </>
  );
}

describe('notification account generation', () => {
  beforeEach(() => vi.mocked(useOptionalAuth).mockReturnValue(baseAuth));
  afterEach(() => vi.clearAllMocks());

  it('계정 전환 뒤 이전 preview 응답과 unread가 새 계정을 덮지 않는다', async () => {
    const oldRequest = deferred<ReturnType<typeof page>>();
    const newRequest = deferred<ReturnType<typeof page>>();
    vi.mocked(listNotifications)
      .mockReturnValueOnce(oldRequest.promise)
      .mockReturnValueOnce(newRequest.promise);

    const { rerender } = render(
      <NotificationProvider>
        <Probe />
      </NotificationProvider>,
    );
    vi.mocked(useOptionalAuth).mockReturnValue({
      ...baseAuth,
      user: { ...baseAuth.user, id: 8, nickname: '여덟별' },
    });
    rerender(
      <NotificationProvider>
        <Probe />
      </NotificationProvider>,
    );

    newRequest.resolve(page(8));
    expect(await screen.findByTestId('message')).toHaveTextContent('계정 8');
    oldRequest.resolve(page(7));
    await Promise.resolve();
    expect(screen.getByTestId('message')).toHaveTextContent('계정 8');
  });

  it('USER_001 조회 실패는 preview·center·unread·canManage를 함께 비우고 진행 요청을 폐기한다', async () => {
    const user = userEvent.setup();
    vi.mocked(listNotifications).mockResolvedValue(page(7));
    render(
      <NotificationProvider>
        <AuthFailureProbe />
      </NotificationProvider>,
    );
    expect(await screen.findByTestId('preview-message')).toHaveTextContent('계정 7');
    await waitFor(() => expect(screen.getByTestId('center-message')).toHaveTextContent('계정 7'));

    vi.mocked(listNotifications).mockRejectedValueOnce(
      new ApiRequestError(404, { code: 'USER_001', message: '사용자를 찾을 수 없습니다.', details: [] }),
    );
    await user.click(screen.getByRole('button', { name: 'refresh-preview' }));

    await waitFor(() => {
      expect(screen.getByTestId('preview-message')).toBeEmptyDOMElement();
      expect(screen.getByTestId('center-message')).toBeEmptyDOMElement();
      expect(screen.getByTestId('unread')).toHaveTextContent('none');
      expect(screen.getByTestId('can-manage')).toHaveTextContent('none');
    });
  });
});
