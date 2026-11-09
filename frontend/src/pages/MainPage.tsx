import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Pagination } from '../components/ui/Pagination';
import { PostCard } from '../components/ui/PostCard';
import { Tabs } from '../components/ui/Tab';
import { getPublicFeed, getUniverseFeed, normalizeDiscoveryKeyword } from '../features/feed/feedApi';
import type { FeedPage, FeedRelation, DiscoverySort } from '../features/feed/types';
import { ApiRequestError } from '../lib/apiClient';
import { useOptionalAuth } from '../lib/authContext';

const PAGE_SIZE = 20;
const SESSION_EXPIRED_MESSAGE = '세션이 만료되었습니다. 다시 로그인해 주세요.';

const TABS: Array<{ key: FeedRelation; label: string }> = [
  { key: 'all', label: '전체' },
  { key: 'friends', label: '친구' },
  { key: 'following', label: '내가 발견한' },
];

interface FeedUrlState {
  relation: FeedRelation;
  keyword: string;
  page: number;
  size: number;
  sort: DiscoverySort;
  canonical: URLSearchParams;
  needsCanonical: boolean;
  keywordError: string | null;
}

interface FeedViewState {
  key: string;
  page: FeedPage | null;
  loading: boolean;
  error: string | null;
  authRequired: boolean;
  canRetry: boolean;
}

function parseNonNegative(value: string | null, fallback: number): number {
  if (value == null || !/^\d+$/u.test(value)) return fallback;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : fallback;
}

function parseSize(value: string | null): number {
  if (value == null || !/^\d+$/u.test(value)) return PAGE_SIZE;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 1 && parsed <= 100 ? parsed : PAGE_SIZE;
}

function parseFeedUrl(searchParams: URLSearchParams, queryKey: string): FeedUrlState {
  const canonical = new URLSearchParams(searchParams);
  const rawRelation = searchParams.get('relation');
  const relation: FeedRelation =
    rawRelation === 'friends' || rawRelation === 'friend'
      ? 'friends'
      : rawRelation === 'following'
        ? 'following'
        : 'all';
  if (rawRelation !== null && rawRelation !== relation) canonical.set('relation', relation);

  const rawKeyword = searchParams.get('keyword');
  let keyword = '';
  let keywordError: string | null = null;
  if (rawKeyword !== null) {
    try {
      keyword = normalizeDiscoveryKeyword(rawKeyword);
      if (keyword) canonical.set('keyword', keyword);
      else canonical.delete('keyword');
    } catch (error) {
      keyword = rawKeyword;
      keywordError = error instanceof Error ? error.message : '검색어를 확인해 주세요.';
    }
  }

  const rawPage = searchParams.get('page');
  const page = parseNonNegative(rawPage, 0);
  if (rawPage !== null && rawPage !== String(page)) canonical.set('page', String(page));
  const rawSize = searchParams.get('size');
  const size = parseSize(rawSize);
  if (rawSize !== null && rawSize !== String(size)) canonical.set('size', String(size));
  const rawSort = searchParams.get('sort');
  const sort: DiscoverySort = rawSort === 'popular' ? 'popular' : 'latest';
  if (rawSort !== null && rawSort !== sort) canonical.set('sort', sort);

  return {
    relation,
    keyword,
    page,
    size,
    sort,
    canonical,
    needsCanonical: canonical.toString() !== queryKey,
    keywordError,
  };
}

function formatDate(value: string | null): string {
  if (!value) return '작성일 미정';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '작성일 미정';
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' }).format(date);
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.code === 'AUTH_002' || error.code === 'AUTH_004') {
      return '로그인 상태를 확인하지 못했습니다. 다시 접속해 주세요.';
    }
    if (error.code === 'USER_001') return '사용자 정보를 확인하지 못했습니다. 다시 시도해 주세요.';
    return error.message || '피드를 불러오지 못했습니다.';
  }
  return error instanceof Error && error.message ? error.message : '피드를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
}

function emptyView(key: string, overrides: Partial<FeedViewState> = {}): FeedViewState {
  return { key, page: null, loading: false, error: null, authRequired: false, canRetry: false, ...overrides };
}

/** `/` 메인 피드의 중앙 열. URL과 서버 snapshot을 화면 상태의 기준으로 삼는다. */
export function MainPage() {
  const auth = useOptionalAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const queryKey = searchParams.toString();
  const urlState = useMemo(() => parseFeedUrl(searchParams, queryKey), [queryKey, searchParams]);
  const authLoading = auth?.isLoading ?? false;
  const viewerId = auth?.user?.id ?? null;
  const isAuthenticated = Boolean(auth?.isAuthenticated && auth.user);
  const authExpired = auth?.sessionExpired ?? false;
  const [draftKeyword, setDraftKeyword] = useState(urlState.keyword);
  const [inputError, setInputError] = useState<string | null>(null);
  const [retryKey, setRetryKey] = useState(0);
  const requestKey = [
    authLoading ? 'auth-loading' : 'auth-ready',
    authExpired ? 'auth-expired' : 'auth-active',
    viewerId ?? 'guest',
    urlState.relation,
    urlState.keyword,
    urlState.page,
    urlState.size,
    urlState.sort,
    retryKey,
  ].join('|');
  const [view, setView] = useState<FeedViewState>(() => emptyView(requestKey));
  const generation = useRef(0);

  useEffect(() => {
    setDraftKeyword(urlState.keyword);
    setInputError(null);
  }, [urlState.keyword]);

  useEffect(() => {
    if (!urlState.needsCanonical || urlState.keywordError) return;
    setSearchParams(urlState.canonical, { replace: true });
  }, [setSearchParams, urlState.canonical, urlState.keywordError, urlState.needsCanonical]);

  useEffect(() => {
    const currentGeneration = ++generation.current;
    let cancelled = false;

    if (authLoading) {
      setView(emptyView(requestKey, { loading: true }));
      return () => {
        cancelled = true;
      };
    }
    if (authExpired) {
      setView(emptyView(requestKey, { error: SESSION_EXPIRED_MESSAGE }));
      return () => {
        cancelled = true;
      };
    }
    if (urlState.needsCanonical && !urlState.keywordError) {
      setView(emptyView(requestKey, { loading: true }));
      return () => {
        cancelled = true;
      };
    }
    if (urlState.keywordError) {
      setView(emptyView(requestKey, { error: urlState.keywordError, canRetry: false }));
      return () => {
        cancelled = true;
      };
    }
    if (!isAuthenticated && urlState.relation !== 'all') {
      setView(emptyView(requestKey, { authRequired: true }));
      return () => {
        cancelled = true;
      };
    }

    setView(emptyView(requestKey, { loading: true }));
    const params = {
      keyword: urlState.keyword,
      page: urlState.page,
      size: urlState.size,
      sort: urlState.sort,
    };
    const request = isAuthenticated
      ? getUniverseFeed({ ...params, relation: urlState.relation })
      : getPublicFeed(params);
    void request
      .then((loaded) => {
        if (cancelled || generation.current !== currentGeneration) return;
        setView(emptyView(requestKey, { page: loaded }));
      })
      .catch((error: unknown) => {
        if (cancelled || generation.current !== currentGeneration) return;
        setView(emptyView(requestKey, { error: errorMessage(error), canRetry: true }));
      });

    return () => {
      cancelled = true;
    };
  }, [
    authLoading,
    authExpired,
    isAuthenticated,
    requestKey,
    setSearchParams,
    urlState.keyword,
    urlState.keywordError,
    urlState.needsCanonical,
    urlState.page,
    urlState.relation,
    urlState.size,
    urlState.sort,
  ]);

  const currentView = view.key === requestKey ? view : emptyView(requestKey, { loading: true });
  const page = currentView.page;
  const paginationPage = page && page.totalPages > 0 ? Math.min(page.page, page.totalPages - 1) : 0;

  const updateUrl = (updates: { relation?: FeedRelation; keyword?: string; page?: number }) => {
    const next = new URLSearchParams(searchParams);
    if (updates.relation) next.set('relation', updates.relation);
    if (updates.keyword) next.set('keyword', updates.keyword);
    else if (updates.keyword === '') next.delete('keyword');
    if (updates.page !== undefined) next.set('page', String(updates.page));
    setSearchParams(next);
  };

  const submitKeyword = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    try {
      const keyword = normalizeDiscoveryKeyword(draftKeyword);
      setInputError(null);
      updateUrl({ keyword, page: 0 });
    } catch (error) {
      setInputError(errorMessage(error));
    }
  };

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <div className="flex items-start gap-2">
        <Tabs
          items={TABS}
          activeKey={urlState.relation}
          onChange={(key) => updateUrl({ relation: key as FeedRelation, page: 0 })}
        />
        <div className="flex-1" />
        <div className="self-center whitespace-nowrap text-xs text-text-muted">
          {page ? `${page.totalElements}개의 신호` : currentView.loading ? '불러오는 중...' : ''}
        </div>
      </div>

      <form
        role="search"
        onSubmit={submitKeyword}
        className="flex border-[2px] border-ink bg-surface-warm shadow-btn-sm"
      >
        <label htmlFor="feed-keyword" className="sr-only">
          피드 검색
        </label>
        <input
          id="feed-keyword"
          type="search"
          value={draftKeyword}
          onChange={(event) => setDraftKeyword(event.target.value)}
          placeholder="제목 또는 작성자 검색"
          className="min-w-0 flex-1 border-0 bg-transparent px-3 py-2 text-[13px] text-ink outline-0"
        />
        <button type="submit" className="border-0 bg-ink px-4 text-xs font-bold text-text-on-ink">
          검색
        </button>
      </form>
      {(inputError || currentView.error) && (
        <div role="alert" className="border-[3px] border-danger bg-danger-bg px-5 py-4 text-[13px] text-danger">
          {inputError ?? currentView.error}
          {currentView.canRetry && (
            <button
              type="button"
              onClick={() => setRetryKey((value) => value + 1)}
              className="ml-3 border-2 border-danger bg-surface px-3 py-1 text-xs font-bold text-danger"
            >
              다시 시도
            </button>
          )}
        </div>
      )}

      {authLoading && (
        <div className="border-[3px] border-ink bg-surface px-6 py-12 text-center text-[13px] text-text-muted">
          인증 상태를 확인하는 중...
        </div>
      )}
      {!authLoading && currentView.authRequired && (
        <div className="border-[3px] border-ink bg-surface px-6 py-12 text-center text-[13px] text-text-body shadow-card">
          <p>친구 피드는 로그인 후 확인할 수 있습니다.</p>
          <Link to="/signin" className="mt-4 inline-block border-2 border-ink bg-accent px-4 py-2 font-bold text-white shadow-btn-sm">
            로그인하기
          </Link>
        </div>
      )}
      {!authLoading && !currentView.authRequired && urlState.relation === 'following' && (
        <p className="text-[12px] text-text-muted">친구를 제외한 내가 발견한 사람의 글입니다.</p>
      )}
      {!authLoading && !currentView.authRequired && currentView.loading && (
        <div aria-live="polite" className="border-[3px] border-ink bg-surface px-6 py-12 text-center text-[13px] text-text-muted shadow-card">
          신호를 불러오는 중...
        </div>
      )}
      {!authLoading && !currentView.authRequired && !currentView.loading && !currentView.error && page && page.items.length === 0 && (
        <div className="border-[3px] border-ink bg-surface px-6 py-16 text-center text-[13px] text-text-muted shadow-card">
          {page.totalElements > 0
            ? '이 페이지에는 도착한 신호가 없습니다. 다른 페이지를 선택해 주세요.'
            : '아직 도착한 신호가 없습니다.'}
        </div>
      )}
      {!authLoading && !currentView.authRequired && !currentView.loading && !currentView.error && page && page.items.length > 0 && (
        <div className="flex flex-col gap-3.5">
          {page.items.map((post) => (
            <PostCard
              key={post.id}
              href={`/blog/${post.blogSlug}/${post.id}`}
              title={post.title}
              meta={`${post.blogTitle} / ${post.category.name} · ${formatDate(post.publishedAt ?? post.createdAt)}`}
              excerpt={post.excerpt}
              visibility={post.visibility}
              tags={post.tags}
              commentCount={post.commentCount}
              likeCount={post.likeCount}
            />
          ))}
        </div>
      )}
      {!authLoading && !currentView.authRequired && !currentView.loading && !currentView.error && page && page.totalPages > 0 && (
        <Pagination page={paginationPage} totalPages={page.totalPages} onChange={(nextPage) => updateUrl({ page: nextPage })} />
      )}
    </div>
  );
}
