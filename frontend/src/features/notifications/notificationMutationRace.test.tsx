import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useState } from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NotificationProvider, useNotificationPreview } from './NotificationProvider';
import { listNotifications, markAllNotificationsRead, markNotificationRead } from './notificationApi';
import { useOptionalAuth } from '../../lib/authContext';

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

function page() {
  return {
    items: [],
    page: 0,
    size: 3,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
    unreadCount: 0,
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
});
