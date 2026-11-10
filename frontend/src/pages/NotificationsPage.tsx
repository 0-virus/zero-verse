import { useEffect, useMemo, useState, type KeyboardEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Pagination } from '../components/ui/Pagination';
import {
  isNotificationAuthFailure,
  isNotificationMutationSuperseded,
  useNotificationCenter,
  type NotificationContextValue,
} from '../features/notifications/NotificationProvider';
import type { NotificationItem, NotificationType } from '../features/notifications/types';
import { ApiRequestError } from '../lib/apiClient';

const PAGE_SIZE = 20;
const UNAVAILABLE_MESSAGE = '더 이상 확인할 수 없는 알림입니다';
type NotificationRetry = () => Promise<void>;
type NoticeState = { message: string; retry?: NotificationRetry };

const ICON: Record<NotificationType, { glyph: string; background: string }> = {
  COMMENT: { glyph: '💬', background: 'bg-info-bg' },
  REPLY: { glyph: '💬', background: 'bg-info-bg' },
  LIKE: { glyph: '♥', background: 'bg-danger-bg' },
  NEIGHBOR: { glyph: '✦', background: 'bg-universe-bg' },
  POST: { glyph: '✦', background: 'bg-universe-bg' },
};

function pageFromSearch(value: string | null): number {
  if (!value || !/^\d+$/u.test(value)) return 0;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : 0;
}
function formatTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '시간 미정';
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.code === 'USER_003') return '정지된 계정은 알림을 읽기 전용으로 확인할 수 있습니다.';
    if (error.code === 'AUTH_002' || error.code === 'AUTH_004' || error.code === 'USER_001') {
      return '로그인이 만료되었습니다. 다시 접속해 주세요.';
    }
    if (error.code === 'NOT_001') return '이 알림은 더 이상 확인할 수 없습니다.';
    return error.message || '알림 요청을 처리하지 못했습니다.';
  }
  return error instanceof Error && error.message ? error.message : '알림 요청을 처리하지 못했습니다.';
}

function displayMessage(item: NotificationItem): string {
  return item.available ? item.message : UNAVAILABLE_MESSAGE;
}

function NotificationRow({
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
  const [busy, setBusy] = useState<'read' | 'delete' | null>(null);
  const icon = ICON[item.type];

  const readAndNavigate = async () => {
    if (busy) return;
    if (context.canManage === false) {
      if (item.available && item.href) navigate(item.href);
      return;
    }
    if (context.canManage !== true) {
      onFailure('정지된 계정은 알림을 읽기 전용으로 확인할 수 있습니다.');
      return;
    }
    setBusy('read');
    const actionGeneration = context.retryGeneration;
    try {
      const current = await context.markRead(item.id);
      onSuccess();
      if (current.available && current.href) navigate(current.href);
    } catch (error) {
      if (!isNotificationMutationSuperseded(error) && context.isRetryGenerationCurrent(actionGeneration)) {
        const retry = isNotificationAuthFailure(error)
          ? undefined
          : context.bindRetry(readAndNavigate, actionGeneration) ?? undefined;
        if (!isNotificationAuthFailure(error) && !retry) return;
        onFailure(errorMessage(error), retry);
      }
    } finally {
      setBusy(null);
    }
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      void readAndNavigate();
    }
  };

  const removeNotification = async () => {
    if (busy) return;
    if (context.canManage !== true) {
      onFailure('정지된 계정은 알림을 읽기 전용으로 확인할 수 있습니다.');
      return;
    }
    setBusy('delete');
    const actionGeneration = context.retryGeneration;
    try {
      await context.remove(item.id);
      onSuccess();
    } catch (error) {
      if (!isNotificationMutationSuperseded(error) && context.isRetryGenerationCurrent(actionGeneration)) {
        const retry = isNotificationAuthFailure(error)
          ? undefined
          : context.bindRetry(removeNotification, actionGeneration) ?? undefined;
        if (!isNotificationAuthFailure(error) && !retry) return;
        onFailure(errorMessage(error), retry);
      }
    } finally {
      setBusy(null);
    }
  };

  const remove = async (event: React.MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    if (busy) return;
    await removeNotification();
  };

  return (
    <div
      role="button"
      tabIndex={0}
      aria-label={displayMessage(item)}
      onClick={() => void readAndNavigate()}
      onKeyDown={onKeyDown}
      className={`flex items-center gap-3.5 border-b border-line px-5 py-[15px] text-left last:border-b-0 ${
        item.readAt ? 'bg-surface font-normal' : 'bg-surface-warm font-semibold'
      } ${busy ? 'cursor-wait opacity-70' : 'cursor-pointer hover:bg-surface-soft'}`}
    >
      <span
        aria-hidden="true"
        className={`h-2 w-2 shrink-0 ${item.readAt ? 'bg-transparent' : 'bg-accent'}`}
      />
      <span
        aria-hidden="true"
        className={`grid h-9 w-9 shrink-0 place-items-center border-2 border-ink text-base ${icon.background}`}
      >
        {icon.glyph}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-[13.5px] leading-[1.5] text-ink">{displayMessage(item)}</span>
        <span className="mt-0.5 block text-[11.5px] font-normal text-text-muted">
          {formatTime(item.createdAt)}
        </span>
      </span>
      <button
        type="button"
        aria-label="알림 삭제"
        disabled={busy !== null || context.canManage !== true}
        onClick={remove}
        onKeyDown={(event) => event.stopPropagation()}
        className="cursor-pointer border-0 bg-transparent px-1 text-[15px] font-normal text-shadow hover:text-danger disabled:cursor-not-allowed disabled:opacity-50"
      >
        ×
      </button>
    </div>
  );
}
export function NotificationsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [notice, setNotice] = useState<NoticeState | null>(null);
  const [readAllBusy, setReadAllBusy] = useState(false);
  const page = useMemo(() => pageFromSearch(searchParams.get('page')), [searchParams]);
  const context = useNotificationCenter(page, PAGE_SIZE);
  const retryGeneration = context?.retryGeneration;
  const center = context?.center ?? { page: null, loading: false, error: null, key: null };
  const data = center?.page ?? null;
  const unreadCount = context?.unreadCount ?? data?.unreadCount ?? null;
  const isLoading = context?.isAuthLoading ?? true;
  const isGuest = !isLoading && !(context?.isAuthenticated ?? false);
  const canManage = context?.canManage === true;

  useEffect(() => {
    setNotice(null);
    setReadAllBusy(false);
  }, [retryGeneration]);

  const updatePage = (nextPage: number) => {
    const next = new URLSearchParams(searchParams);
    if (nextPage === 0) next.delete('page');
    else next.set('page', String(nextPage));
    setSearchParams(next);
  };

  const retry = () => {
    if (!context) return;
    const generation = context.retryGeneration;
    const retryAction = context.bindRetry(() => context.refreshCenter(page, PAGE_SIZE), generation);
    if (!retryAction) return;
    setNotice(null);
    void retryAction().catch(() => undefined);
  };

  const readAll = async () => {
    if (!context || context.canManage !== true || context.unreadCount === 0) return;
    const actionGeneration = context.retryGeneration;
    setNotice(null);
    setReadAllBusy(true);
    try {
      await context.markAllRead();
      setNotice(null);
    } catch (error) {
      if (!isNotificationMutationSuperseded(error) && context.isRetryGenerationCurrent(actionGeneration)) {
        const retry = isNotificationAuthFailure(error)
          ? undefined
          : context.bindRetry(readAll, actionGeneration) ?? undefined;
        if (!isNotificationAuthFailure(error) && !retry) return;
        setNotice({
          message: errorMessage(error),
          retry,
        });
      }
    } finally {
      setReadAllBusy(false);
    }
  };

  if (isLoading) {
    return (
      <section aria-label="알림 센터">
        <h1 className="text-[23px] font-bold">알림 센터</h1>
        <div className="mt-6 border-[3px] border-ink bg-surface px-6 py-12 text-center text-[13px] text-text-muted shadow-panel">
          인증 상태를 확인하는 중...
        </div>
      </section>
    );
  }

  if (isGuest || !context) {
    return (
      <section aria-label="알림 센터">
        <h1 className="text-[23px] font-bold">알림 센터</h1>
        <div className="mt-6 border-[3px] border-ink bg-surface px-6 py-12 text-center text-[13px] text-text-muted shadow-panel">
          알림을 확인하려면 로그인이 필요합니다.
        </div>
      </section>
    );
  }

  return (
    <section aria-label="알림 센터">
      <div className="mb-[18px] flex items-center gap-3">
        <h1 className="text-[23px] font-bold">알림 센터</h1>
        <span className="border-2 border-ink bg-accent px-[9px] py-0.5 text-[11px] font-bold text-white">
          읽지 않음 {unreadCount ?? '—'}
        </span>
        <div className="flex-1" />
        <button
          type="button"
          onClick={() => void readAll()}
          disabled={!canManage || unreadCount === null || unreadCount === 0 || center.loading || readAllBusy}
          className="border-[3px] border-ink bg-surface px-4 py-[7px] text-xs font-bold text-ink shadow-btn hover:bg-surface-raise disabled:cursor-not-allowed disabled:opacity-50"
        >
          모두 읽음 처리
        </button>
      </div>

      {context.isReadOnly && (
        <p role="status" className="mb-4 border-2 border-warning bg-warning-bg px-4 py-2 text-xs text-warning">
          정지된 계정은 알림을 읽기 전용으로 확인할 수 있습니다.
        </p>
      )}
      {notice && (
        <div role="alert" className="mb-4 border-2 border-danger bg-danger-bg px-4 py-2 text-xs text-danger">
          {notice.message}
          {notice.retry && (
            <button type="button" onClick={() => void notice.retry?.()} className="ml-3 border-2 border-danger bg-surface px-2 py-1 font-bold">
              다시 시도
            </button>
          )}
        </div>
      )}
      {Boolean(center.error) && !center.loading && (
        <div role="alert" className="mb-4 border-[3px] border-danger bg-danger-bg px-5 py-4 text-[13px] text-danger">
          {errorMessage(center.error)}
          {!context.isAuthFailure && (
            <button type="button" onClick={retry} className="ml-3 border-2 border-danger bg-surface px-3 py-1 text-xs font-bold">
              다시 시도
            </button>
          )}
        </div>
      )}

      {center.loading && !data && (
        <div aria-live="polite" className="border-[3px] border-ink bg-surface px-6 py-12 text-center text-[13px] text-text-muted shadow-panel">
          알림을 불러오는 중...
        </div>
      )}
      {!center.loading && data && data.items.length === 0 && (
        <div className="border-[3px] border-ink bg-surface px-6 py-16 text-center text-[13px] text-text-muted shadow-panel">
          {data.totalElements > 0 ? '이 페이지에는 알림이 없습니다. 다른 페이지를 선택해 주세요.' : '아직 알림이 없습니다.'}
        </div>
      )}
      {data && data.items.length > 0 && (
        <div className="border-[3px] border-ink bg-surface shadow-panel">
          {data.items.map((item) => (
            <NotificationRow
              key={item.id}
              item={item}
              context={context}
              onFailure={(message, retryAction) => setNotice({ message, retry: retryAction })}
              onSuccess={() => setNotice(null)}
            />
          ))}
          {!data.hasNext && (
            <div className="border-t border-line px-3 py-3 text-center text-xs text-text-muted">
              알림을 모두 확인했어요 ✦
            </div>
          )}
        </div>
      )}
      {data && data.totalPages > 0 && (
        <div className="mt-6">
          <Pagination
            page={Math.min(data.page, data.totalPages - 1)}
            totalPages={data.totalPages}
            onChange={updatePage}
          />
        </div>
      )}
    </section>
  );
}
