import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Avatar } from '../components/ui/Avatar';
import { Badge } from '../components/ui/Badge';
import { Pagination } from '../components/ui/Pagination';
import { Table, type TableColumn } from '../components/ui/Table';
import { isAdminPermissionError, isAdminActorNotFound, useAdminAccess } from '../features/admin/AdminAccessProvider';
import { adminApi, DEFAULT_ADMIN_PAGE_SIZE, normalizeAdminKeyword } from '../features/admin/adminApi';
import type { AdminUserListResponse, AdminUserSummary } from '../features/admin/types';
import { ApiRequestError } from '../lib/apiClient';

interface AdminListView {
  key: string;
  response: AdminUserListResponse | null;
  loading: boolean;
  error: unknown | null;
  canRetry: boolean;
}

function parsePage(value: string | null): number {
  if (!value || !/^\d+$/u.test(value)) return 0;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : 0;
}

function parseSize(value: string | null): number {
  if (!value || !/^\d+$/u.test(value)) return DEFAULT_ADMIN_PAGE_SIZE;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 1 && parsed <= 100 ? parsed : DEFAULT_ADMIN_PAGE_SIZE;
}

function formatDate(value: string): string {
  const date = value.slice(0, 10);
  return /^\d{4}-\d{2}-\d{2}$/u.test(date) ? date.replaceAll('-', '.') : '날짜 미정';
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.code === 'ADMIN_001') return '현재 관리자 권한이 없습니다.';
    if (error.code === 'USER_003') return '정지된 계정은 관리자 화면을 사용할 수 없습니다.';
    if (error.code === 'USER_001') return '현재 로그인 계정을 확인할 수 없습니다. 다시 접속해 주세요.';
    if (error.code === 'AUTH_002' || error.code === 'AUTH_004') return '로그인이 만료되었습니다. 다시 접속해 주세요.';
    return error.message || '사용자 목록을 불러오지 못했습니다.';
  }
  return error instanceof Error && error.message ? error.message : '사용자 목록을 불러오지 못했습니다.';
}

function initialView(key: string): AdminListView {
  return { key, response: null, loading: false, error: null, canRetry: false };
}

function UserCell({ user }: { user: AdminUserSummary }) {
  return (
    <div className="flex min-w-0 items-center gap-2">
      {user.profileImageUrl ? (
        <img
          src={user.profileImageUrl}
          alt=""
          className="h-7 w-7 shrink-0 border-2 border-ink object-cover"
        />
      ) : (
        <Avatar emoji="🛰" size={28} label={`${user.nickname} 아바타`} />
      )}
      <div className="min-w-0">
        <b className="block truncate">{user.nickname}</b>
        <span className="block truncate text-[11px] text-text-muted">{user.email}</span>
      </div>
    </div>
  );
}

/** `/admin` — 현재 권한이 확인된 관리자용 사용자 목록. */
export function AdminPage() {
  const access = useAdminAccess();
  const [searchParams, setSearchParams] = useSearchParams();
  const queryKey = searchParams.toString();
  const keyword = useMemo(() => searchParams.get('keyword')?.trim() ?? '', [queryKey, searchParams]);
  const page = useMemo(() => parsePage(searchParams.get('page')), [queryKey, searchParams]);
  const size = useMemo(() => parseSize(searchParams.get('size')), [queryKey, searchParams]);
  const [draftKeyword, setDraftKeyword] = useState(keyword);
  const [inputError, setInputError] = useState<string | null>(null);
  const [retryKey, setRetryKey] = useState(0);
  const [view, setView] = useState<AdminListView>(() => initialView('initial'));
  const requestGeneration = useRef(0);

  useEffect(() => {
    setDraftKeyword(keyword);
    setInputError(null);
  }, [keyword]);

  const requestKey = [access.status, access.viewerId ?? 'guest', access.generation, keyword, page, size, retryKey].join('|');

  useEffect(() => {
    const currentRequest = ++requestGeneration.current;
    let cancelled = false;
    const accessGeneration = access.generation;
    const viewerId = access.viewerId;

    if (access.status !== 'allowed') {
      setView((previous) => ({ ...initialView(requestKey), response: previous.response, loading: access.isLoading }));
      return () => {
        cancelled = true;
      };
    }

    setView((previous) => ({
      ...initialView(requestKey),
      response: previous.response,
      loading: true,
    }));
    void adminApi.list({ keyword, page, size })
      .then((response) => {
        if (cancelled || currentRequest !== requestGeneration.current || !access.isCurrent(accessGeneration, viewerId)) return;
        setView({ key: requestKey, response, loading: false, error: null, canRetry: false });
        if (response.users.totalPages > 0 && page >= response.users.totalPages) {
          const next = new URLSearchParams(searchParams);
          const lastPage = response.users.totalPages - 1;
          if (lastPage === 0) next.delete('page');
          else next.set('page', String(lastPage));
          setSearchParams(next, { replace: true });
        }
      })
      .catch(async (caught: unknown) => {
        if (cancelled || currentRequest !== requestGeneration.current || !access.isCurrent(accessGeneration, viewerId)) return;
        if (isAdminPermissionError(caught) || isAdminActorNotFound(caught)) {
          await access.revalidateAfterError(caught);
          return;
        }
        setView((previous) => ({
          ...previous,
          key: requestKey,
          loading: false,
          error: caught,
          canRetry: true,
        }));
      });

    return () => {
      cancelled = true;
    };
  }, [access, keyword, page, requestKey, searchParams, setSearchParams, size]);

  const currentView = view.key === requestKey ? view : initialView(requestKey);
  const response = currentView.response;
  const users = response?.users;

  const updateQuery = (updates: { keyword?: string; page?: number }) => {
    const next = new URLSearchParams(searchParams);
    if (updates.keyword === undefined) {
      // preserve current keyword
    } else if (updates.keyword) {
      next.set('keyword', updates.keyword);
    } else {
      next.delete('keyword');
    }
    if (updates.page === undefined || updates.page === 0) next.delete('page');
    else next.set('page', String(updates.page));
    setSearchParams(next);
  };

  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    try {
      const normalized = normalizeAdminKeyword(draftKeyword);
      setInputError(null);
      updateQuery({ keyword: normalized, page: 0 });
    } catch (caught) {
      setInputError(errorMessage(caught));
    }
  };

  const columns: TableColumn<AdminUserSummary>[] = [
    { key: 'user', header: '사용자', width: '32%', render: (user) => <UserCell user={user} /> },
    {
      key: 'blog',
      header: '블로그',
      width: '25%',
      render: (user) => <span className="text-text-body">{user.defaultBlog?.title ?? '블로그 없음'}</span>,
    },
    { key: 'role', header: '역할', width: '14%', render: (user) => <Badge kind={user.role} /> },
    { key: 'status', header: '상태', width: '14%', render: (user) => <Badge kind={user.status} /> },
    { key: 'joined', header: '가입일', width: '15%', render: (user) => <span className="text-xs text-text-muted">{formatDate(user.createdAt)}</span> },
    {
      key: 'action',
      header: '',
      width: '90px',
      render: (user) => (
        <Link
          to={`/admin/users/${user.id}`}
          className="block border-2 border-ink bg-surface px-2 py-1 text-center text-xs font-semibold text-ink hover:bg-surface-raise"
        >
          관리
        </Link>
      ),
    },
  ];

  return (
    <section aria-label="관리자 콘솔">
      <h1 className="sr-only">관리자</h1>
      <div className="mb-[18px] flex items-center gap-3">
        <span className="font-pixel text-[11px] tracking-[1px] text-ink">ADMIN CONSOLE</span>
        <span className="bg-ink px-[9px] py-[3px] text-[10px] font-bold tracking-[2px] text-white">ROLE: ADMIN</span>
        <div className="flex-1" />
        <form role="search" onSubmit={submitSearch}>
          <label htmlFor="admin-user-search" className="sr-only">관리자 사용자 검색</label>
          <input
            id="admin-user-search"
            type="search"
            value={draftKeyword}
            onChange={(event) => {
              setDraftKeyword(event.target.value);
              setInputError(null);
            }}
            placeholder="이메일 · 닉네임 검색"
            className="w-[240px] border-2 border-ink bg-surface px-3 py-2 text-[12.5px] outline-0 shadow-btn-sm"
          />
        </form>
      </div>
      {inputError && <p role="alert" className="mb-4 text-[13px] text-danger">{inputError}</p>}

      {response && (
        <div className="mb-[18px] flex gap-3.5">
          <StatCard label="전체 사용자" value={response.summary.totalUsers} />
          <StatCard label="이번 주 가입" value={`+${response.summary.joinedThisWeek}`} tone="accent" />
          <StatCard label="정지 계정" value={response.summary.suspendedUsers} tone="danger" />
        </div>
      )}
      {!response && currentView.loading && <AdminLoading label="사용자 목록을 불러오는 중..." />}
      {currentView.error !== null && (
        <div role="alert" className="mb-5 border-[3px] border-danger bg-danger-bg px-5 py-4 text-[13px] text-danger shadow-card">
          {errorMessage(currentView.error)}
          {currentView.canRetry && (
            <button type="button" onClick={() => setRetryKey((value) => value + 1)} className="ml-3 border-2 border-danger bg-surface px-3 py-1 text-xs font-bold text-danger">
              다시 시도
            </button>
          )}
        </div>
      )}
      {response && users && users.items.length === 0 && (
        <div className="border-[3px] border-ink bg-surface px-6 py-12 text-center text-[13px] text-text-muted shadow-panel">
          검색 결과가 없습니다.
        </div>
      )}
      {response && users && users.items.length > 0 && (
        <>
          <Table columns={columns} rows={users.items} rowKey={(user) => String(user.id)} />
          <div className="mt-6">
            <Pagination page={Math.min(users.page, Math.max(0, users.totalPages - 1))} totalPages={users.totalPages} onChange={(nextPage) => updateQuery({ page: nextPage })} />
          </div>
        </>
      )}
      {response && currentView.loading && <p className="mt-3 text-right text-xs text-text-muted">목록을 갱신하는 중...</p>}
    </section>
  );
}

function StatCard({ label, value, tone }: { label: string; value: string | number; tone?: 'accent' | 'danger' }) {
  return (
    <div className="flex-1 border-[3px] border-ink bg-surface px-[18px] py-3.5 shadow-card">
      <div className="text-[11px] font-bold tracking-[2px] text-text-muted">{label}</div>
      <div className={`text-[26px] font-bold ${tone === 'accent' ? 'text-accent' : tone === 'danger' ? 'text-danger' : ''}`}>{value}</div>
    </div>
  );
}

function AdminLoading({ label }: { label: string }) {
  return <div className="border-[3px] border-ink bg-surface px-6 py-12 text-center text-[13px] text-text-muted shadow-panel">{label}</div>;
}
