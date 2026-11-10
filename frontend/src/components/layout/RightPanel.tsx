import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Panel } from '../ui/Panel';
import type { DefaultBlog } from '../../types/auth';
import { useOptionalAuth } from '../../lib/authContext';
import { listUniverse } from '../../features/universe/universeApi';
import type { UniverseListKind } from '../../features/universe/types';
import {
  isNotificationAuthFailure,
  isNotificationMutationSuperseded,
  useNotificationPreview,
  type NotificationContextValue,
} from '../../features/notifications/NotificationProvider';
import type { NotificationItem } from '../../features/notifications/types';
import { ApiRequestError } from '../../lib/apiClient';

type NotificationRetry = () => Promise<void>;

function notificationErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.code === 'USER_003') return '정지된 계정은 알림을 읽기 전용으로 확인할 수 있습니다.';
    if (error.code === 'USER_001' || error.code === 'AUTH_002' || error.code === 'AUTH_004') {
      return '로그인이 만료되었습니다. 다시 접속해 주세요.';
    }
    return error.message || '알림 요청을 처리하지 못했습니다.';
  }
  return error instanceof Error && error.message ? error.message : '알림을 읽지 못했습니다.';
}

/** `/` 오른쪽 레일의 서버 관계 현황과 M8 공유 알림 preview. */
type StatKey = 'friends' | 'following' | 'followers';
type StatState = Record<StatKey, { value: number | null; loading: boolean; error: boolean }>;

const STAT_ITEMS: Array<{ key: StatKey; label: string; kind: UniverseListKind }> = [
  { key: 'friends', label: '친구', kind: 'friends' },
  { key: 'following', label: '내가 발견한', kind: 'following' },
  { key: 'followers', label: '나를 발견한', kind: 'followers' },
];

const IDLE_STATS: StatState = {
  friends: { value: null, loading: false, error: false },
  following: { value: null, loading: false, error: false },
  followers: { value: null, loading: false, error: false },
};

function notificationGlyph(item: NotificationItem): string {
  if (item.type === 'LIKE') return '♥';
  if (item.type === 'NEIGHBOR' || item.type === 'POST') return '✦';
  return '💬';
}

function RecentNotification({
  item,
  context,
  onFailure,
  onSuccess,
}: {
  item: NotificationItem;
  context: NotificationContextValue;
  onFailure: (message: string, retry?: NotificationRetry) => void;
  onSuccess: () => void;
}) {
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const handleClick = async () => {
    if (busy) return;
    if (context.canManage === false) {
      if (item.available && item.href) navigate(item.href);
      return;
    }
    if (context.canManage !== true) {
      onFailure('정지된 계정은 알림을 읽기 전용으로 확인할 수 있습니다.');
      return;
    }
    const actionGeneration = context.retryGeneration;
    setBusy(true);
    try {
      const current = await context.markRead(item.id);
      onSuccess();
      if (current.available && current.href) navigate(current.href);
    } catch (error) {
      if (!isNotificationMutationSuperseded(error) && context.isRetryGenerationCurrent(actionGeneration)) {
        const retry = isNotificationAuthFailure(error)
          ? undefined
          : context.bindRetry(() => handleClick(), actionGeneration) ?? undefined;
        if (!isNotificationAuthFailure(error) && !retry) return;
        onFailure(
          notificationErrorMessage(error),
          retry ?? undefined,
        );
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <button
      type="button"
      onClick={() => void handleClick()}
      disabled={busy}
      className="flex w-full gap-2.5 border-b border-line px-3.5 py-[11px] text-left text-[12.5px] leading-[1.5] text-text-body hover:bg-surface-soft disabled:cursor-wait disabled:opacity-60"
    >
      <span aria-hidden="true" className="text-accent">{notificationGlyph(item)}</span>
      <span>{item.available ? item.message : '더 이상 확인할 수 없는 알림입니다'}</span>
    </button>
  );
}

function loadingStats(): StatState {
  return {
    friends: { value: null, loading: true, error: false },
    following: { value: null, loading: true, error: false },
    followers: { value: null, loading: true, error: false },
  };
}

export function RightPanel({ blog }: { blog?: DefaultBlog | null }) {
  const auth = useOptionalAuth();
  const notifications = useNotificationPreview();
  const viewerId = auth?.user?.id ?? null;
  const authLoading = auth?.isLoading ?? false;
  const [stats, setStats] = useState<StatState>(IDLE_STATS);
  const [statsViewerId, setStatsViewerId] = useState<number | null>(null);
  const [notificationError, setNotificationError] = useState<string | null>(null);
  const [notificationRetry, setNotificationRetry] = useState<NotificationRetry | null>(null);
  const generation = useRef(0);

  useEffect(() => {
    setNotificationError(null);
    setNotificationRetry(null);
  }, [notifications?.retryGeneration]);

  useEffect(() => {
    const currentGeneration = ++generation.current;
    if (authLoading || viewerId == null) {
      setStats(IDLE_STATS);
      setStatsViewerId(null);
      return () => undefined;
    }

    setStatsViewerId(viewerId);
    setStats(loadingStats());
    STAT_ITEMS.forEach(({ key, kind }) => {
      void listUniverse(kind, { page: 0, size: 1 })
        .then((page) => {
          if (generation.current !== currentGeneration) return;
          setStats((previous) => ({
            ...previous,
            [key]: { value: page.totalElements, loading: false, error: false },
          }));
        })
        .catch(() => {
          if (generation.current !== currentGeneration) return;
          setStats((previous) => ({
            ...previous,
            [key]: { value: null, loading: false, error: true },
          }));
        });
    });

    return () => {
      generation.current += 1;
    };
  }, [authLoading, viewerId]);

  const visibleStats = statsViewerId === viewerId ? stats : loadingStats();
  const retryPreview = () => {
    if (!notifications) return;
    const retry = notifications.bindRetry(
      () => notifications.refreshPreview(),
      notifications.retryGeneration,
    );
    if (retry) void retry().catch(() => undefined);
  };

  return (
    <aside
      aria-label="사이드 패널"
      style={{ position: 'sticky', top: 20, gap: 18 }}
      className="flex w-[300px] shrink-0 flex-col self-start"
    >
      <Panel title="내 블로그">
        {blog ? (
          <div className="p-3.5">
            <p className="mb-[3px] text-[15px] font-bold text-ink">{blog.title}</p>
            <p className="mb-3 text-xs text-text-muted">/blog/{blog.urlSlug}</p>
            <div className="flex gap-2">
              <Link
                to="/settings"
                className="flex-1 border-2 border-ink bg-surface py-[7px] text-center text-xs font-semibold text-ink hover:bg-surface-raise"
              >
                블로그 관리
              </Link>
              <Link
                to="/write"
                className="flex-1 border-2 border-ink bg-accent py-[7px] text-center text-xs font-bold text-white hover:brightness-108"
              >
                새 글 작성
              </Link>
            </div>
          </div>
        ) : (
          <p className="px-5 py-8 text-center text-[13px] text-text-muted">
            블로그 정보를 불러오면 여기에 표시됩니다.
          </p>
        )}
      </Panel>

      <Panel
        title="최근 알림"
        actions={
          notifications?.unreadCount !== null && notifications?.unreadCount !== undefined ? (
            <span className="text-[11px] font-bold text-accent">읽지 않음 {notifications.unreadCount}</span>
          ) : undefined
        }
      >
        {notificationError && (
          <div role="alert" className="border-b border-line bg-danger-bg px-3.5 py-2 text-xs text-danger">
            {notificationError}
            {notificationRetry && (
              <button
                type="button"
                onClick={() => {
                  const retry = notificationRetry;
                  setNotificationError(null);
                  setNotificationRetry(null);
                  void retry();
                }}
                className="ml-2 border-2 border-danger bg-surface px-2 py-1 font-bold"
              >
                다시 시도
              </button>
            )}
          </div>
        )}
        {notifications?.isAuthLoading && (
          <p className="px-5 py-8 text-center text-[13px] text-text-muted">인증 상태를 확인하는 중...</p>
        )}
        {notifications?.isAuthFailure && (
          <p className="px-5 py-8 text-center text-[13px] text-danger">
            로그인이 만료되었습니다. 다시 접속해 주세요.
          </p>
        )}
        {!notifications?.isAuthLoading && !notifications?.isAuthFailure && (!notifications || !notifications.isAuthenticated) && (
          <p className="px-5 py-8 text-center text-[13px] text-text-muted">아직 알림이 없습니다.</p>
        )}
        {notifications?.isAuthenticated && !notifications.isAuthFailure && notifications.preview.loading && !notifications.preview.page && (
          <p className="px-5 py-8 text-center text-[13px] text-text-muted">알림을 불러오는 중...</p>
        )}
        {notifications?.isAuthenticated && !notifications.isAuthFailure && Boolean(notifications.preview.error) && !notifications.preview.loading && (
          <div className="px-3.5 py-5 text-center text-xs text-danger">
            알림을 불러오지 못했습니다.
            <button
              type="button"
              onClick={retryPreview}
              className="ml-2 border-2 border-danger bg-surface px-2 py-1 font-bold"
            >
              다시 시도
            </button>
          </div>
        )}
        {notifications?.isAuthenticated && !notifications.isAuthFailure && notifications.preview.page && notifications.preview.page.items.length === 0 && !notifications.preview.loading && (
          <p className="px-5 py-8 text-center text-[13px] text-text-muted">아직 알림이 없습니다.</p>
        )}
        {notifications?.isAuthenticated && !notifications.isAuthFailure && notifications.preview.page && notifications.preview.page.items.length > 0 && (
          <div>
            {notifications.preview.page.items.slice(0, 3).map((item) => (
              <RecentNotification
                key={item.id}
                item={item}
                context={notifications}
                onFailure={(message, retry) => {
                  setNotificationError(message);
                  setNotificationRetry(retry ? () => retry : null);
                }}
                onSuccess={() => {
                  setNotificationError(null);
                  setNotificationRetry(null);
                }}
              />
            ))}
          </div>
        )}
        {notifications?.isAuthenticated && !notifications.isAuthFailure && notifications.preview.page && !notifications.preview.loading && !notifications.preview.error && (
          <Link
            to="/notifications"
            className="block px-3.5 py-2.5 text-center text-xs text-text-muted hover:text-accent"
          >
            알림 센터 열기 →
          </Link>
        )}
      </Panel>

      <Panel title="유니버스 현황">
        {authLoading ? (
          <p className="px-5 py-8 text-center text-[13px] text-text-muted">인증 상태를 확인하는 중...</p>
        ) : viewerId == null ? (
          <p className="px-5 py-8 text-center text-[13px] text-text-muted">
            로그인하면 유니버스 현황을 확인할 수 있습니다.
          </p>
        ) : (
          <>
            <div className="flex gap-2 p-3.5 text-center">
              {STAT_ITEMS.map(({ key, label }) => {
                const stat = visibleStats[key];
                return (
                  <div key={key} className="flex-1 border-2 border-line px-0 py-2.5">
                    <div
                      className={
                        key === 'friends'
                          ? 'text-[20px] font-bold text-accent'
                          : 'text-[20px] font-bold text-ink'
                      }
                    >
                      {stat.loading ? '…' : stat.error ? '오류' : stat.value ?? '—'}
                    </div>
                    <div className="text-[11px] text-text-muted">{label}</div>
                  </div>
                );
              })}
            </div>
            <p className="px-3.5 pb-3 text-[11px] leading-[1.5] text-text-muted">
              내가 발견한 수에는 친구가 포함됩니다.
            </p>
          </>
        )}
      </Panel>
    </aside>
  );
}
