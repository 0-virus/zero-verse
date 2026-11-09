import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Pagination } from '../components/ui/Pagination';
import { PostCard } from '../components/ui/PostCard';
import { normalizeSearchKeyword, search } from '../features/search/searchApi';
import type {
  MatchedType,
  SearchResponse,
  SearchSort,
  SearchType,
} from '../features/search/types';
import { ApiRequestError } from '../lib/apiClient';
import { useOptionalAuth } from '../lib/authContext';

const PAGE_SIZE = 20;
const SESSION_EXPIRED_MESSAGE = '세션이 만료되었습니다. 다시 로그인해 주세요.';
const TYPES: Array<{ key: SearchType; label: string }> = [
  { key: 'ALL', label: '전체' },
  { key: 'POST', label: '글' },
  { key: 'BLOG', label: '블로그' },
  { key: 'USER', label: '사용자' },
  { key: 'TAG', label: '태그' },
];

const TYPE_LABEL: Record<MatchedType, string> = {
  POST: '글',
  BLOG: '블로그',
  USER: '사용자',
  TAG: '태그',
};

const TYPE_BADGE_CLASS: Record<MatchedType, string> = {
  POST: 'border-success bg-success-bg text-success',
  BLOG: 'border-universe bg-universe-bg text-universe',
  USER: 'border-warning bg-warning-bg text-warning',
  TAG: 'border-info bg-info-bg text-info',
};

interface SearchUrlState {
  keyword: string;
  type: SearchType;
  page: number;
  size: number;
  sort: SearchSort;
  canonical: URLSearchParams;
  needsCanonical: boolean;
  keywordError: string | null;
}

interface SearchViewState {
  key: string;
  response: SearchResponse | null;
  loading: boolean;
  error: string | null;
  canRetry: boolean;
}

function parseInteger(value: string | null, fallback: number, min: number, max?: number): number {
  if (value == null || !/^\d+$/u.test(value)) return fallback;
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < min || (max !== undefined && parsed > max)) return fallback;
  return parsed;
}

function parseSearchUrl(searchParams: URLSearchParams, queryKey: string): SearchUrlState {
  const canonical = new URLSearchParams(searchParams);
  const rawKeyword = searchParams.get('keyword');
  let keyword = '';
  let keywordError: string | null = null;
  if (rawKeyword !== null) {
    try {
      keyword = normalizeSearchKeyword(rawKeyword);
      if (keyword) canonical.set('keyword', keyword);
      else canonical.delete('keyword');
    } catch (error) {
      keyword = rawKeyword;
      keywordError = error instanceof Error ? error.message : '검색어를 확인해 주세요.';
    }
  }

  const rawType = searchParams.get('type');
  const type: SearchType = TYPES.some(({ key }) => key === rawType)
    ? (rawType as SearchType)
    : 'ALL';
  if (rawType !== null && rawType !== type) canonical.set('type', type);

  const rawPage = searchParams.get('page');
  const page = parseInteger(rawPage, 0, 0);
  if (rawPage !== null && rawPage !== String(page)) canonical.set('page', String(page));
  const rawSize = searchParams.get('size');
  const size = parseInteger(rawSize, PAGE_SIZE, 1, 100);
  if (rawSize !== null && rawSize !== String(size)) canonical.set('size', String(size));
  const rawSort = searchParams.get('sort');
  const sort: SearchSort = rawSort === 'popular' ? 'popular' : 'latest';
  if (rawSort !== null && rawSort !== sort) canonical.set('sort', sort);

  return {
    keyword,
    type,
    page,
    size,
    sort,
    canonical,
    needsCanonical: canonical.toString() !== queryKey,
    keywordError,
  };
}

function formatDate(value: string | null): string {
  if (!value) return '임시저장';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '작성일 미정';
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' }).format(date);
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.code === 'AUTH_002' || error.code === 'AUTH_004') {
      return '로그인 상태를 확인하지 못했습니다. 이전 검색 결과를 폐기했습니다.';
    }
    if (error.code === 'USER_001') return '사용자 정보를 확인하지 못했습니다. 이전 검색 결과를 폐기했습니다.';
    return error.message || '검색 결과를 불러오지 못했습니다.';
  }
  return error instanceof Error && error.message ? error.message : '검색 결과를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
}

function emptyView(key: string, overrides: Partial<SearchViewState> = {}): SearchViewState {
  return { key, response: null, loading: false, error: null, canRetry: false, ...overrides };
}

/** `/search` — 서버 snapshot counts를 표시하는 게시글 중심 통합 검색. */
export function SearchPage() {
  const auth = useOptionalAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const queryKey = searchParams.toString();
  const urlState = useMemo(() => parseSearchUrl(searchParams, queryKey), [queryKey, searchParams]);
  const authLoading = auth?.isLoading ?? false;
  const viewerId = auth?.user?.id ?? null;
  const authExpired = auth?.sessionExpired ?? false;
  const [draftKeyword, setDraftKeyword] = useState(urlState.keyword);
  const [inputError, setInputError] = useState<string | null>(null);
  const [retryKey, setRetryKey] = useState(0);
  const requestKey = [
    authLoading ? 'auth-loading' : 'auth-ready',
    authExpired ? 'auth-expired' : 'auth-active',
    viewerId ?? 'guest',
    urlState.keyword,
    urlState.type,
    urlState.page,
    urlState.size,
    urlState.sort,
    retryKey,
  ].join('|');
  const [view, setView] = useState<SearchViewState>(() => emptyView(requestKey));
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
      setView(emptyView(requestKey, { error: urlState.keywordError }));
      return () => {
        cancelled = true;
      };
    }
    if (!urlState.keyword) {
      setView(emptyView(requestKey));
      return () => {
        cancelled = true;
      };
    }

    setView(emptyView(requestKey, { loading: true }));
    void search({
      keyword: urlState.keyword,
      type: urlState.type,
      page: urlState.page,
      size: urlState.size,
      sort: urlState.sort,
    })
      .then((response) => {
        if (cancelled || generation.current !== currentGeneration) return;
        setView(emptyView(requestKey, { response }));
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
    requestKey,
    setSearchParams,
    urlState.keyword,
    urlState.keywordError,
    urlState.needsCanonical,
    urlState.page,
    urlState.size,
    urlState.sort,
    urlState.type,
  ]);

  const currentView = view.key === requestKey ? view : emptyView(requestKey, { loading: true });
  const response = currentView.response;
  const paginationPage = response && response.totalPages > 0
    ? Math.min(response.page, response.totalPages - 1)
    : 0;

  const updateUrl = (updates: { keyword?: string; type?: SearchType; page?: number }) => {
    const next = new URLSearchParams(searchParams);
    if (updates.keyword) next.set('keyword', updates.keyword);
    else if (updates.keyword === '') next.delete('keyword');
    if (updates.type) next.set('type', updates.type);
    if (updates.page !== undefined) next.set('page', String(updates.page));
    setSearchParams(next);
  };

  const submitKeyword = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    try {
      const keyword = normalizeSearchKeyword(draftKeyword);
      setInputError(null);
      updateUrl({ keyword, page: 0 });
    } catch (error) {
      setInputError(errorMessage(error));
    }
  };

  return (
    <section>
      <p className="font-pixel text-[10px] tracking-[2px] text-universe">SCANNING UNIVERSE…</p>
      {!urlState.keyword ? (
        <>
          <h1 className="mt-3 text-[23px] font-bold">검색</h1>
          <form role="search" onSubmit={submitKeyword} className="mt-5 flex border-[3px] border-ink bg-surface shadow-btn">
            <label htmlFor="search-page-keyword" className="sr-only">검색어</label>
            <input
              id="search-page-keyword"
              type="search"
              value={draftKeyword}
              onChange={(event) => setDraftKeyword(event.target.value)}
              placeholder="유니버스 전체 검색 — 글 · 블로그 · 사용자 · 태그"
              className="min-w-0 flex-1 border-0 bg-transparent px-3 py-[9px] text-[13px] outline-0"
            />
            <button type="submit" className="border-0 bg-ink px-[18px] text-xs font-bold tracking-[2px] text-text-on-ink">검색</button>
          </form>
          {(inputError || currentView.error) && <p role="alert" className="mt-4 text-[13px] text-danger">{inputError ?? currentView.error}</p>}
          <p className="mt-6 border-[3px] border-ink bg-surface px-6 py-12 text-center text-[13px] text-text-muted shadow-card">
            검색어를 입력하면 유니버스의 글을 찾을 수 있습니다.
          </p>
        </>
      ) : (
        <>
          <h1 className="mt-3 text-[23px] font-bold">
            '{urlState.keyword}' 검색 결과 <span className="text-accent">{response?.counts.ALL ?? '…'}</span>
          </h1>
          <p className="mt-2 text-[13px] text-text-body">블로그·사용자·태그 조건에 맞는 글을 검색합니다.</p>
          <div role="tablist" aria-label="검색 범위" className="mt-[18px] flex gap-2">
            {TYPES.map(({ key, label }) => {
              const count = response?.counts[key];
              const active = key === urlState.type;
              return (
                <button
                  key={key}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => updateUrl({ type: key, page: 0 })}
                  className={`border-[3px] border-ink px-4 py-[7px] text-[12.5px] font-bold shadow-btn ${
                    active ? 'bg-ink text-text-on-ink' : 'bg-surface text-ink hover:bg-surface-raise'
                  }`}
                >
                  {label}{count === undefined ? '' : ` ${count}`}
                </button>
              );
            })}
          </div>
          {(inputError || currentView.error) && (
            <div role="alert" className="mt-4 border-[3px] border-danger bg-danger-bg px-5 py-4 text-[13px] text-danger">
              {inputError ?? currentView.error}
              {currentView.canRetry && (
                <button type="button" onClick={() => setRetryKey((value) => value + 1)} className="ml-3 border-2 border-danger bg-surface px-3 py-1 text-xs font-bold text-danger">
                  다시 시도
                </button>
              )}
            </div>
          )}
          {authLoading && <p className="mt-6 border-[3px] border-ink bg-surface px-6 py-12 text-center text-[13px] text-text-muted shadow-card">인증 상태를 확인하는 중...</p>}
          {!authLoading && currentView.loading && <p className="mt-6 border-[3px] border-ink bg-surface px-6 py-12 text-center text-[13px] text-text-muted shadow-card">검색 결과를 불러오는 중...</p>}
          {!authLoading && !currentView.loading && !currentView.error && response && response.items.length === 0 && (
            <p className="mt-6 border-[3px] border-ink bg-surface px-6 py-12 text-center text-[13px] text-text-muted shadow-card">
              {response.totalElements > 0
                ? '이 페이지에는 검색 결과가 없습니다. 다른 페이지를 선택해 주세요.'
                : '일치하는 신호가 없습니다.'}
            </p>
          )}
          {!authLoading && !currentView.loading && !currentView.error && response && response.items.length > 0 && (
            <div className="mt-[18px] flex flex-col gap-3.5">
              {response.items.map((post) => {
                const matchedTypes = post.matchedTypes;
                const selectedBadge = (urlState.type === 'ALL' ? matchedTypes[0] : (urlState.type as MatchedType)) ?? 'POST';
                const isOwnDraft = post.publishedAt === null && post.author.id === viewerId;
                const href = isOwnDraft
                  ? `/edit/${post.id}`
                  : `/blog/${post.blogSlug}/${post.id}`;
                return (
                  <PostCard
                    key={post.id}
                    href={href}
                    variant="search"
                    title={post.title}
                    meta={`${post.blogTitle} · ${post.author.nickname} · ${formatDate(post.publishedAt ?? post.createdAt)}`}
                    excerpt={post.excerpt}
                    visibility={post.visibility}
                    tags={post.tags}
                    commentCount={post.commentCount}
                    likeCount={post.likeCount}
                    leadingBadge={
                      <>
                        {isOwnDraft && (
                          <span className="inline-block border-2 border-warning bg-warning-bg px-2 py-[3px] text-[10px] font-bold tracking-[1px] text-warning">
                            임시저장
                          </span>
                        )}
                        <span className={`inline-block border-2 px-2 py-[3px] text-[10px] font-bold tracking-[2px] ${TYPE_BADGE_CLASS[selectedBadge]}`}>
                          {TYPE_LABEL[selectedBadge]}
                        </span>
                      </>
                    }
                    footerExtra={
                      <span className="mr-2 text-[11px] text-text-muted">
                        {urlState.type === 'ALL'
                          ? `전체 매칭: ${matchedTypes.map((type) => TYPE_LABEL[type]).join(' · ')}`
                          : `검색 범위: ${TYPE_LABEL[selectedBadge]}`}
                      </span>
                    }
                  />
                );
              })}
            </div>
          )}
          {!authLoading && !currentView.loading && !currentView.error && response && response.totalPages > 0 && (
            <div className="mt-5">
              <Pagination page={paginationPage} totalPages={response.totalPages} onChange={(page) => updateUrl({ page })} />
            </div>
          )}
        </>
      )}
    </section>
  );
}
