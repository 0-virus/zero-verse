import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { useOptionalAuth } from '../../lib/authContext';
import {
  deleteNotification,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from './notificationApi';
import type { NotificationItem, NotificationPage } from './types';

export interface NotificationLayerState {
  page: NotificationPage | null;
  loading: boolean;
  error: unknown | null;
  key: string | null;
}

interface LayerQuery {
  page: number;
  size: number;
}

export interface NotificationContextValue {
  preview: NotificationLayerState;
  center: NotificationLayerState;
  unreadCount: number | null;
  canManage: boolean | null;
  isReadOnly: boolean;
  isAuthLoading: boolean;
  isAuthenticated: boolean;
  viewerId: number | null;
  refreshPreview: () => Promise<void>;
  refreshCenter: (page?: number, size?: number) => Promise<void>;
  markRead: (notificationId: number) => Promise<NotificationItem>;
  markAllRead: () => Promise<void>;
  remove: (notificationId: number) => Promise<void>;
  invalidate: () => Promise<void>;
  setCenterActive: (active: boolean) => void;
}

const NotificationContext = createContext<NotificationContextValue | null>(null);

const EMPTY_LAYER: NotificationLayerState = {
  page: null,
  loading: false,
  error: null,
  key: null,
};

export class NotificationMutationSupersededError extends Error {
  constructor() {
    super('알림 mutation이 현재 계정 세대로 대체되었습니다.');
    this.name = 'NotificationMutationSupersededError';
  }
}

export function isNotificationMutationSuperseded(error: unknown): boolean {
  return error instanceof NotificationMutationSupersededError;
}

interface MutationSnapshot {
  accountGeneration: number;
  viewerId: number | null;
}

function errorForReadOnly(): Error {
  return new Error('정지된 계정은 알림을 읽기 전용으로 확인할 수 있습니다.');
}

function isCurrent(
  accountGeneration: number,
  mutationGeneration: number,
  currentAccountGeneration: { current: number },
  currentMutationGeneration: { current: number },
): boolean {
  return (
    accountGeneration === currentAccountGeneration.current &&
    mutationGeneration === currentMutationGeneration.current
  );
}

/** M8 공통 알림 상태. preview와 센터 page를 분리하되 unread summary는 한 세대 순서로 공유한다. */
export function NotificationProvider({ children }: { children: ReactNode }) {
  const auth = useOptionalAuth();
  const isAuthLoading = auth?.isLoading ?? false;
  const viewerId = auth?.user?.id ?? null;
  const isAuthenticated = Boolean(auth?.isAuthenticated && auth.user && !auth.sessionExpired);
  const authKey = isAuthLoading
    ? 'loading'
    : auth?.sessionExpired
      ? 'expired'
      : isAuthenticated && viewerId !== null
        ? `viewer:${viewerId}`
        : 'guest';

  const [preview, setPreview] = useState<NotificationLayerState>(EMPTY_LAYER);
  const [center, setCenter] = useState<NotificationLayerState>(EMPTY_LAYER);
  const [unreadCount, setUnreadCount] = useState<number | null>(null);
  const [canManage, setCanManage] = useState<boolean | null>(null);
  const accountGeneration = useRef(0);
  const mutationGeneration = useRef(0);
  const summaryRequestGeneration = useRef(0);
  const appliedSummaryGeneration = useRef(0);
  const previewRequestGeneration = useRef(0);
  const centerRequestGeneration = useRef(0);
  const inFlight = useRef<{
    preview: { key: string; promise: Promise<void> } | null;
    center: { key: string; promise: Promise<void> } | null;
  }>({ preview: null, center: null });
  const mutationInFlight = useRef(new Map<string, Promise<unknown>>());
  const centerQuery = useRef<LayerQuery>({ page: 0, size: 20 });
  const centerActive = useRef(false);

  useLayoutEffect(() => {
    accountGeneration.current += 1;
    mutationGeneration.current += 1;
    appliedSummaryGeneration.current = 0;
    setPreview(EMPTY_LAYER);
    setCenter(EMPTY_LAYER);
    setUnreadCount(null);
    setCanManage(null);
    inFlight.current = { preview: null, center: null };
    mutationInFlight.current.clear();
  }, [authKey]);

  const ready = !isAuthLoading && isAuthenticated && viewerId !== null;

  const applySummary = useCallback(
    (
      page: NotificationPage,
      requestGeneration: number,
      requestAccountGeneration: number,
      requestMutationGeneration: number,
    ) => {
      if (
        requestGeneration < appliedSummaryGeneration.current ||
        !isCurrent(
          requestAccountGeneration,
          requestMutationGeneration,
          accountGeneration,
          mutationGeneration,
        )
      ) {
        return;
      }
      appliedSummaryGeneration.current = requestGeneration;
      setUnreadCount(page.unreadCount);
      setCanManage(page.canManage);
    },
    [],
  );

  const refreshPreview = useCallback(async (): Promise<void> => {
    if (!ready) return;
    const requestAccountGeneration = accountGeneration.current;
    const requestMutationGeneration = mutationGeneration.current;
    const key = `${viewerId}:preview:${requestAccountGeneration}:${requestMutationGeneration}`;
    if (inFlight.current.preview?.key === key) {
      return inFlight.current.preview.promise;
    }

    const requestGeneration = ++previewRequestGeneration.current;
    const summaryGeneration = ++summaryRequestGeneration.current;
    const promise = (async () => {
      setPreview((previous) => ({
        page: previous.key === key ? previous.page : null,
        loading: true,
        error: null,
        key,
      }));
      try {
        const loaded = await listNotifications({ read: 'all', page: 0, size: 3 });
        if (
          requestGeneration !== previewRequestGeneration.current ||
          !isCurrent(
            requestAccountGeneration,
            requestMutationGeneration,
            accountGeneration,
            mutationGeneration,
          )
        ) {
          return;
        }
        setPreview({ page: loaded, loading: false, error: null, key });
        applySummary(loaded, summaryGeneration, requestAccountGeneration, requestMutationGeneration);
      } catch (error) {
        if (
          requestGeneration === previewRequestGeneration.current &&
          isCurrent(
            requestAccountGeneration,
            requestMutationGeneration,
            accountGeneration,
            mutationGeneration,
          )
        ) {
          setPreview((previous) => ({ ...previous, loading: false, error, key }));
        }
      }
    })();
    inFlight.current.preview = { key, promise };
    try {
      await promise;
    } finally {
      if (inFlight.current.preview?.promise === promise) inFlight.current.preview = null;
    }
  }, [applySummary, isAuthenticated, ready, viewerId]);

  const refreshCenter = useCallback(
    async (page = 0, size = 20): Promise<void> => {
      if (!ready) return;
      centerQuery.current = { page, size };
      const requestAccountGeneration = accountGeneration.current;
      const requestMutationGeneration = mutationGeneration.current;
      const key = `${viewerId}:center:${page}:${size}:${requestAccountGeneration}:${requestMutationGeneration}`;
      if (inFlight.current.center?.key === key) {
        return inFlight.current.center.promise;
      }

      const requestGeneration = ++centerRequestGeneration.current;
      const summaryGeneration = ++summaryRequestGeneration.current;
      const promise = (async () => {
        setCenter((previous) => ({
          page: previous.key === key ? previous.page : null,
          loading: true,
          error: null,
          key,
        }));
        try {
          const loaded = await listNotifications({ read: 'all', page, size });
          if (
            requestGeneration !== centerRequestGeneration.current ||
            !isCurrent(
              requestAccountGeneration,
              requestMutationGeneration,
              accountGeneration,
              mutationGeneration,
            )
          ) {
            return;
          }
          setCenter({ page: loaded, loading: false, error: null, key });
          applySummary(loaded, summaryGeneration, requestAccountGeneration, requestMutationGeneration);
        } catch (error) {
          if (
            requestGeneration === centerRequestGeneration.current &&
            isCurrent(
              requestAccountGeneration,
              requestMutationGeneration,
              accountGeneration,
              mutationGeneration,
            )
          ) {
            setCenter((previous) => ({ ...previous, loading: false, error, key }));
          }
        }
      })();
      inFlight.current.center = { key, promise };
      try {
        await promise;
      } finally {
        if (inFlight.current.center?.promise === promise) inFlight.current.center = null;
      }
    },
    [applySummary, isAuthenticated, ready, viewerId],
  );

  const invalidate = useCallback(async (snapshot?: MutationSnapshot): Promise<void> => {
    if (snapshot && snapshot.accountGeneration !== accountGeneration.current) return;
    mutationGeneration.current += 1;
    appliedSummaryGeneration.current = 0;
    setPreview(EMPTY_LAYER);
    setCenter((previous) => ({ ...EMPTY_LAYER, key: previous.key }));
    setUnreadCount(null);
    setCanManage(null);
    if (!ready || (snapshot && snapshot.accountGeneration !== accountGeneration.current)) return;
    const centerAtStart = centerQuery.current;
    const refreshes: Array<Promise<void>> = [refreshPreview()];
    if (centerActive.current) {
      refreshes.push(refreshCenter(centerAtStart.page, centerAtStart.size));
    }
    await Promise.allSettled(refreshes);
  }, [ready, refreshCenter, refreshPreview]);

  const setCenterActive = useCallback((active: boolean) => {
    centerActive.current = active;
  }, []);

  const runMutation = useCallback(
    async <T,>(key: string, operation: (snapshot: MutationSnapshot) => Promise<T>): Promise<T> => {
      const snapshot: MutationSnapshot = {
        accountGeneration: accountGeneration.current,
        viewerId,
      };
      const scopedKey = `${snapshot.accountGeneration}:${snapshot.viewerId ?? 'guest'}:${key}`;
      const existing = mutationInFlight.current.get(scopedKey);
      if (existing) return existing as Promise<T>;
      const promise = (async () => {
        const result = await operation(snapshot);
        if (snapshot.accountGeneration !== accountGeneration.current) {
          throw new NotificationMutationSupersededError();
        }
        return result;
      })();
      mutationInFlight.current.set(scopedKey, promise);
      try {
        return await promise;
      } finally {
        if (mutationInFlight.current.get(scopedKey) === promise) mutationInFlight.current.delete(scopedKey);
      }
    },
    [viewerId],
  );

  const markRead = useCallback(
    (notificationId: number) =>
      runMutation(`read:${notificationId}`, async (snapshot) => {
        if (canManage !== true) throw canManage === false ? errorForReadOnly() : new Error('알림 관리 권한을 확인하는 중입니다.');
        const item = await markNotificationRead(notificationId);
        if (snapshot.accountGeneration !== accountGeneration.current) {
          throw new NotificationMutationSupersededError();
        }
        await invalidate(snapshot);
        return item;
      }),
    [canManage, invalidate, runMutation],
  );

  const markAllRead = useCallback(
    () =>
      runMutation('read-all', async (snapshot) => {
        if (canManage !== true) throw canManage === false ? errorForReadOnly() : new Error('알림 관리 권한을 확인하는 중입니다.');
        await markAllNotificationsRead();
        if (snapshot.accountGeneration !== accountGeneration.current) {
          throw new NotificationMutationSupersededError();
        }
        await invalidate(snapshot);
      }),
    [canManage, invalidate, runMutation],
  );

  const removeById = useCallback(
    (notificationId: number) =>
      runMutation(`delete:${notificationId}`, async (snapshot) => {
        if (canManage !== true) throw canManage === false ? errorForReadOnly() : new Error('알림 관리 권한을 확인하는 중입니다.');
        await deleteNotification(notificationId);
        if (snapshot.accountGeneration !== accountGeneration.current) {
          throw new NotificationMutationSupersededError();
        }
        await invalidate(snapshot);
      }),
    [canManage, invalidate, runMutation],
  );

  const value = useMemo<NotificationContextValue>(
    () => ({
      preview,
      center,
      unreadCount,
      canManage,
      isReadOnly: canManage === false,
      isAuthLoading,
      isAuthenticated,
      viewerId,
      refreshPreview,
      refreshCenter,
      markRead,
      markAllRead,
      remove: removeById,
      invalidate,
      setCenterActive,
    }),
    [
      canManage,
      center,
      invalidate,
      isAuthLoading,
      isAuthenticated,
      markAllRead,
      markRead,
      preview,
      refreshCenter,
      refreshPreview,
      removeById,
      setCenterActive,
      unreadCount,
      viewerId,
    ],
  );

  useEffect(() => {
    if (ready) void refreshPreview();
  }, [ready, refreshPreview]);

  useEffect(() => {
    const onVisibilityChange = () => {
      if (document.visibilityState !== 'visible') return;
      void refreshPreview();
      if (centerActive.current) {
        const query = centerQuery.current;
        void refreshCenter(query.page, query.size);
      }
    };
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => document.removeEventListener('visibilitychange', onVisibilityChange);
  }, [refreshCenter, refreshPreview]);

  return <NotificationContext.Provider value={value}>{children}</NotificationContext.Provider>;
}

export function useOptionalNotifications(): NotificationContextValue | null {
  return useContext(NotificationContext);
}

export function useNotifications(): NotificationContextValue {
  const context = useOptionalNotifications();
  if (!context) throw new Error('useNotifications는 NotificationProvider 안에서만 사용할 수 있습니다.');
  return context;
}

export function useNotificationPreview(): NotificationContextValue | null {
  return useOptionalNotifications();
}

export function useNotificationCenter(page = 0, size = 20): NotificationContextValue | null {
  const context = useOptionalNotifications();
  const isAuthenticated = context?.isAuthenticated ?? false;
  const isAuthLoading = context?.isAuthLoading ?? false;
  const refreshCenter = context?.refreshCenter;
  const setCenterActive = context?.setCenterActive;
  useEffect(() => {
    if (!isAuthenticated || isAuthLoading || !refreshCenter || !setCenterActive) return undefined;
    setCenterActive(true);
    void refreshCenter(page, size);
    return () => setCenterActive(false);
  }, [isAuthLoading, isAuthenticated, page, refreshCenter, setCenterActive, size]);
  return context;
}

export function useOptionalNotificationActions(): Pick<NotificationContextValue, 'invalidate'> | null {
  const context = useOptionalNotifications();
  return context ? { invalidate: context.invalidate } : null;
}

