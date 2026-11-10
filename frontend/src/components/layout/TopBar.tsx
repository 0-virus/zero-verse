import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { normalizeSearchKeyword } from '../../features/search/searchApi';
import {
  isNotificationAuthFailure,
  isNotificationMutationSuperseded,
  useNotificationPreview,
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

/**
 * 전 화면 공통 상단바.
 *
 * 값은 `ZeroVerse Main Feed v2.dc.html`의 TOP BAR 인라인 style이 정본이다
 * (DESIGN-SYSTEM §6.1, PRD §6.6).
 *
 * - 높이 64px, `#fff8ec`, 하단 3px 잉크 보더, `padding:0 28px`, `gap:20px`, `z-index:10`.
 * - 좌: 14px 정사각 accent 마크(`4px 4px 0 shadow, -3px 3px 0 ink`) + `ZERO`(잉크)`VERSE`(accent),
 *   Press Start 2P 15px `letter-spacing:1px`.
 * - 중앙: 520px 검색바 — 3px 보더 + `shadow-btn`, 흰 입력 + 잉크 검색 버튼(`#ffd9a0`, `tracking:2px`).
 * - 우(gap 12px): `✎ 글쓰기`(accent) / `알림`(중립) / `user.nickname`(중립, 비로그인은 `로그인`).
 *
 * **관리자 전용 버튼은 없다**(PRD §9-K 폐기). `/admin` 진입은 프로필 메뉴 경유.
 *
 * 검색 submit은 `/search?keyword=...`로 이동하고, 빈 입력은 API를 호출하지 않는다(M7).
 */
export function TopBar({ nickname }: { nickname?: string | null }) {
  const isSignedIn = Boolean(nickname);
  const profileLabel = nickname || '로그인';
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [keyword, setKeyword] = useState(searchParams.get('keyword') ?? '');
  const [error, setError] = useState<string | null>(null);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [notificationError, setNotificationError] = useState<string | null>(null);
  const [notificationRetry, setNotificationRetry] = useState<NotificationRetry | null>(null);
  const [notificationBusyId, setNotificationBusyId] = useState<number | null>(null);
  const notifications = useNotificationPreview();

  useEffect(() => {
    setKeyword(searchParams.get('keyword') ?? '');
    setError(null);
  }, [searchParams.toString()]);

  useEffect(() => {
    if (!notificationOpen) return undefined;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setNotificationOpen(false);
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [notificationOpen]);

  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    try {
      const normalized = normalizeSearchKeyword(keyword);
      if (!normalized) {
        setError('검색어를 입력해 주세요.');
        return;
      }
      const params = new URLSearchParams();
      params.set('keyword', normalized);
      setError(null);
      navigate(`/search?${params.toString()}`);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '검색어를 확인해 주세요.');
    }
  };

  const toggleNotifications = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    const nextOpen = !notificationOpen;
    setNotificationOpen(nextOpen);
    setNotificationError(null);
    setNotificationRetry(null);
    if (nextOpen) void notifications?.refreshPreview();
  };

  const openNotification = async (item: NotificationItem) => {
    if (notificationBusyId === item.id) return;
    if (notifications?.canManage === false) {
      setNotificationOpen(false);
      if (item.available && item.href) navigate(item.href);
      return;
    }
    if (!notifications || notifications.canManage !== true) {
      setNotificationError('정지된 계정은 알림을 읽기 전용으로 확인할 수 있습니다.');
      setNotificationRetry(null);
      return;
    }
    setNotificationBusyId(item.id);
    setNotificationError(null);
    setNotificationRetry(null);
    try {
      const current = await notifications.markRead(item.id);
      setNotificationError(null);
      setNotificationRetry(null);
      setNotificationOpen(false);
      if (current.available && current.href) navigate(current.href);
    } catch (caught) {
      if (!isNotificationMutationSuperseded(caught)) {
        setNotificationError(notificationErrorMessage(caught));
        setNotificationRetry(
          isNotificationAuthFailure(caught) ? null : () => () => openNotification(item),
        );
      }
    } finally {
      setNotificationBusyId(null);
    }
  };

  const preview = notifications?.preview.page;
  const unreadCount = notifications?.unreadCount;
  const canRenderPreview = Boolean(
    notifications?.isAuthenticated && !notifications.isAuthLoading && !notifications.isAuthFailure,
  );

  return (
    <header
      style={{ height: 64, padding: '0 28px', gap: 20, zIndex: 10 }}
      className="relative flex items-center border-b-[3px] border-ink bg-surface-warm"
    >
      <Link to="/" aria-label="ZEROVERSE" className="flex items-center gap-2.5">
        <span
          aria-hidden="true"
          style={{
            width: 14,
            height: 14,
            background: 'var(--color-accent)',
            boxShadow: '4px 4px 0 var(--color-shadow), -3px 3px 0 var(--color-ink)',
          }}
        />
        <span className="font-pixel text-[15px] tracking-[1px] text-ink">
          ZERO<span className="text-accent">VERSE</span>
        </span>
      </Link>

      <div className="flex flex-1 justify-center">
        <form
          role="search"
          onSubmit={submitSearch}
          style={{ width: 520 }}
          className="flex border-[3px] border-ink bg-surface shadow-btn"
        >
          <input
            type="search"
            aria-label="검색"
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value);
              setError(null);
            }}
            placeholder="유니버스 전체 검색 — 글 · 블로그 · 사용자 · 태그"
            className="min-w-0 flex-1 border-0 bg-transparent px-3 py-[9px] text-[13px] text-ink outline-0"
          />
          <button
            type="submit"
            style={{ padding: '0 18px' }}
            className="shrink-0 cursor-pointer border-0 bg-ink text-xs font-bold tracking-[2px] text-text-on-ink"
          >
            검색
          </button>
        </form>
        {error && (
          <span role="alert" className="absolute mt-[58px] text-xs text-danger">
            {error}
          </span>
        )}
      </div>

      <nav aria-label="사용자 메뉴" className="flex items-center gap-3">
        <Link
          to="/write"
          style={{ padding: '7px 16px' }}
          className="border-[3px] border-ink bg-accent text-[13px] font-bold text-white shadow-btn hover:brightness-108"
        >
          ✎ 글쓰기
        </Link>
        <Link
          to="/notifications"
          aria-label="알림"
          aria-haspopup="dialog"
          aria-expanded={notificationOpen}
          onClick={toggleNotifications}
          style={{ padding: '7px 14px' }}
          className="relative border-[3px] border-ink bg-surface text-[13px] font-semibold text-ink shadow-btn hover:bg-surface-raise"
        >
          알림
          {unreadCount !== undefined && unreadCount !== null && unreadCount > 0 && (
            <span
              aria-hidden="true"
              className="absolute -right-2 -top-2 grid h-[17px] w-[17px] place-items-center border-2 border-ink bg-accent text-[10px] font-bold text-white"
            >
              {unreadCount}
            </span>
          )}
        </Link>
        <Link
          to={isSignedIn ? '/settings' : '/signin'}
          style={{ padding: '7px 14px' }}
          className="border-[3px] border-ink bg-surface text-[13px] font-semibold text-ink shadow-btn hover:bg-surface-raise"
        >
          {profileLabel}
        </Link>
      </nav>
      {notificationOpen && (
        <div
          role="dialog"
          aria-label="최근 알림"
          style={{ top: 70, right: 28, width: 330 }}
          className="absolute z-[99] border-[3px] border-ink bg-surface shadow-card"
        >
          <div className="flex items-center justify-between border-b-[3px] border-ink px-3.5 py-2.5">
            <span className="text-[13px] font-bold tracking-[2px] text-ink">■ 알림</span>
            <Link
              to="/notifications"
              onClick={() => setNotificationOpen(false)}
              className="text-[11px] text-accent hover:text-accent-strong"
            >
              알림 센터 →
            </Link>
          </div>
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
          {notifications?.isAuthFailure && (
            <p className="px-3.5 py-6 text-center text-xs text-danger">
              로그인이 만료되었습니다. 다시 접속해 주세요.
            </p>
          )}
          {!canRenderPreview && !notifications?.isAuthFailure && (
            <p className="px-3.5 py-6 text-center text-xs text-text-muted">
              로그인하면 알림을 확인할 수 있습니다.
            </p>
          )}
          {canRenderPreview && notifications?.preview.loading && !preview && (
            <p className="px-3.5 py-6 text-center text-xs text-text-muted">알림을 불러오는 중...</p>
          )}
          {canRenderPreview && Boolean(notifications?.preview.error) && !notifications?.preview.loading && (
            <div className="px-3.5 py-4 text-center text-xs text-danger">
              알림을 불러오지 못했습니다.
              <button
                type="button"
                onClick={() => void notifications?.refreshPreview()}
                className="ml-2 border-2 border-danger bg-surface px-2 py-1 font-bold"
              >
                다시 시도
              </button>
            </div>
          )}
          {canRenderPreview && preview && preview.items.length === 0 && !notifications?.preview.loading && (
            <p className="px-3.5 py-6 text-center text-xs text-text-muted">아직 알림이 없습니다.</p>
          )}
          {canRenderPreview && preview && preview.items.length > 0 && (
            <div>
              {preview.items.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  disabled={notificationBusyId === item.id}
                  onClick={() => void openNotification(item)}
                  className="flex w-full gap-2.5 border-b border-line px-3.5 py-[11px] text-left text-[12.5px] leading-[1.5] text-text-body hover:bg-surface-soft disabled:cursor-wait disabled:opacity-60"
                >
                  <span aria-hidden="true" className="text-accent">{item.type === 'LIKE' ? '♥' : item.type === 'NEIGHBOR' || item.type === 'POST' ? '✦' : '💬'}</span>
                  <span>{item.available ? item.message : '더 이상 확인할 수 없는 알림입니다'}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </header>
  );
}
