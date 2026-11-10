import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useState } from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NotificationProvider, useNotificationPreview } from './NotificationProvider';
import { listNotifications, markAllNotificationsRead, markNotificationRead } from './notificationApi';
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

function authFor(id: number) {
  return { ...baseAuth, user: { ...baseAuth.user, id, nickname: `별${id}` } };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

function page(unreadCount = 0, marker = '') {
  return {
    items: marker
      ? [{
          id: 7,
          type: 'LIKE' as const,
          message: marker,
          actor: null,
          targetType: 'POST' as const,
          targetId: 7,
          href: '/blog/seven/7',
          available: true,
          readAt: null,
          createdAt: '2026-11-10T00:00:00Z',
        }]
      : [],
    page: 0,
    size: 3,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
    unreadCount,
    canManage: true,
  };
}

function Probe() {
  const context = useNotificationPreview();
  const [result, setResult] = useState('');
  return (
    <>
      <output data-testid="can-manage">{String(context?.canManage ?? 'none')}</output>
      <output data-testid="result">{result}</output>
      <button
        type="button"
        onClick={() => {
          void context?.markRead(7).then((item) => setResult(item.href ?? '')).catch(() => undefined);
        }}
      >
        read
      </button>
      <button
        type="button"
        onClick={() => {
          void context?.markAllRead().then(() => setResult('done')).catch(() => undefined);
        }}
      >
        read-all
      </button>
    </>
  );
}

function PreviewRaceProbe() {
  const context = useNotificationPreview();
  return (
    <>
      <output data-testid="race-unread">{context?.unreadCount ?? 'none'}</output>
      <output data-testid="race-message">{context?.preview.page?.items[0]?.message ?? ''}</output>
      <button type="button" onClick={() => void context?.refreshPreview()}>refresh-preview</button>
      <button type="button" onClick={() => void context?.markRead(7)}>read</button>
      <button type="button" onClick={() => void context?.markAllRead()}>read-all</button>
    </>
  );
}

describe('NotificationProvider mutation account generation', () => {
  beforeEach(() => {
    vi.mocked(useOptionalAuth).mockReturnValue(authFor(7));
    vi.mocked(listNotifications).mockResolvedValue(page());
  });

  afterEach(() => vi.clearAllMocks());

  it('계정 전환 뒤 read mutation을 합치지 않고 이전 href를 반환하지 않는다', async () => {
    const user = userEvent.setup();
    const oldRead = deferred<Awaited<ReturnType<typeof markNotificationRead>>>();
    const newRead = deferred<Awaited<ReturnType<typeof markNotificationRead>>>();
    vi.mocked(markNotificationRead)
      .mockReturnValueOnce(oldRead.promise)
      .mockReturnValueOnce(newRead.promise);
    const { rerender } = render(
      <NotificationProvider>
        <Probe />
      </NotificationProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('can-manage')).toHaveTextContent('true'));
    await user.click(screen.getByRole('button', { name: 'read' }));

    vi.mocked(useOptionalAuth).mockReturnValue(authFor(8));
    rerender(
      <NotificationProvider>
        <Probe />
      </NotificationProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('can-manage')).toHaveTextContent('true'));
    await user.click(screen.getByRole('button', { name: 'read' }));

    await act(async () => {
      newRead.resolve({
        id: 7,
        type: 'LIKE',
        message: 'B',
        actor: null,
        targetType: 'POST',
        targetId: 8,
        href: '/blog/account-b',
        available: true,
        readAt: '2026-11-10T00:00:00Z',
        createdAt: '2026-11-10T00:00:00Z',
      });
    });
    await waitFor(() => expect(screen.getByTestId('result')).toHaveTextContent('/blog/account-b'));

    await act(async () => {
      oldRead.resolve({
        id: 7,
        type: 'LIKE',
        message: 'A',
        actor: null,
        targetType: 'POST',
        targetId: 7,
        href: '/blog/account-a',
        available: true,
        readAt: '2026-11-10T00:00:00Z',
        createdAt: '2026-11-10T00:00:00Z',
      });
    });
    await act(async () => undefined);
    expect(screen.getByTestId('result')).toHaveTextContent('/blog/account-b');
    expect(markNotificationRead).toHaveBeenCalledTimes(2);
  });

  it('계정 전환 뒤 read-all mutation을 합치지 않고 이전 invalidate를 적용하지 않는다', async () => {
    const user = userEvent.setup();
    const oldReadAll = deferred<null>();
    const newReadAll = deferred<null>();
    vi.mocked(markAllNotificationsRead)
      .mockReturnValueOnce(oldReadAll.promise)
      .mockReturnValueOnce(newReadAll.promise);
    const { rerender } = render(
      <NotificationProvider>
        <Probe />
      </NotificationProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('can-manage')).toHaveTextContent('true'));
    await user.click(screen.getByRole('button', { name: 'read-all' }));

    vi.mocked(useOptionalAuth).mockReturnValue(authFor(8));
    rerender(
      <NotificationProvider>
        <Probe />
      </NotificationProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('can-manage')).toHaveTextContent('true'));
    await user.click(screen.getByRole('button', { name: 'read-all' }));

    await act(async () => newReadAll.resolve(null));
    await waitFor(() => expect(screen.getByTestId('result')).toHaveTextContent('done'));
    await act(async () => oldReadAll.resolve(null));
    await act(async () => undefined);

    expect(screen.getByTestId('result')).toHaveTextContent('done');
    expect(markAllNotificationsRead).toHaveBeenCalledTimes(2);
  });

  it('USER_001 mutation 실패도 현재 알림 cache와 관리 상태를 비운다', async () => {
    const user = userEvent.setup();
    render(
      <NotificationProvider>
        <Probe />
      </NotificationProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('can-manage')).toHaveTextContent('true'));
    vi.mocked(markNotificationRead).mockRejectedValueOnce(
      new ApiRequestError(404, { code: 'USER_001', message: '사용자를 찾을 수 없습니다.', details: [] }),
    );

    await user.click(screen.getByRole('button', { name: 'read' }));

    await waitFor(() => {
      expect(screen.getByTestId('can-manage')).toHaveTextContent('none');
      expect(screen.getByTestId('result')).toHaveTextContent('');
    });
  });

  it('동일 계정의 mutation 전 지연 GET은 성공한 read 이후 상태를 되돌리지 않는다', async () => {
    const user = userEvent.setup();
    const preRead = deferred<ReturnType<typeof page>>();
    vi.mocked(listNotifications)
      .mockResolvedValueOnce(page(1, '기존 알림'))
      .mockReturnValueOnce(preRead.promise)
      .mockResolvedValueOnce(page(0, '읽음 반영'));
    vi.mocked(markNotificationRead).mockResolvedValue({
      id: 7,
      type: 'LIKE',
      message: '읽음 반영',
      actor: null,
      targetType: 'POST',
      targetId: 7,
      href: '/blog/seven/7',
      available: true,
      readAt: '2026-11-10T01:00:00Z',
      createdAt: '2026-11-10T00:00:00Z',
    });
    render(
      <NotificationProvider>
        <PreviewRaceProbe />
      </NotificationProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('race-unread')).toHaveTextContent('1'));

    await user.click(screen.getByRole('button', { name: 'refresh-preview' }));
    await waitFor(() => expect(listNotifications).toHaveBeenCalledTimes(2));
    await user.click(screen.getByRole('button', { name: 'read' }));

    await waitFor(() => {
      expect(screen.getByTestId('race-unread')).toHaveTextContent('0');
      expect(screen.getByTestId('race-message')).toHaveTextContent('읽음 반영');
    });
    await act(async () => preRead.resolve(page(1, '오래된 응답')));
    await act(async () => undefined);
    expect(screen.getByTestId('race-unread')).toHaveTextContent('0');
    expect(screen.getByTestId('race-message')).toHaveTextContent('읽음 반영');
  });

  it('read-all canonical 재조회가 신규 unread를 0으로 덮어쓰지 않는다', async () => {
    const user = userEvent.setup();
    const afterReadAll = deferred<ReturnType<typeof page>>();
    vi.mocked(listNotifications)
      .mockResolvedValueOnce(page(1, '기존 알림'))
      .mockReturnValueOnce(afterReadAll.promise);
    vi.mocked(markAllNotificationsRead).mockResolvedValue(null);
    render(
      <NotificationProvider>
        <PreviewRaceProbe />
      </NotificationProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('race-unread')).toHaveTextContent('1'));

    await user.click(screen.getByRole('button', { name: 'read-all' }));
    await waitFor(() => expect(markAllNotificationsRead).toHaveBeenCalledTimes(1));
    await act(async () => afterReadAll.resolve(page(1, '새 알림')));

    await waitFor(() => {
      expect(screen.getByTestId('race-unread')).toHaveTextContent('1');
      expect(screen.getByTestId('race-message')).toHaveTextContent('새 알림');
    });
  });
});
