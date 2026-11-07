import { useEffect, useMemo, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { PostCard } from '../components/ui/PostCard';
import { getPublicBlog } from '../features/blog/blogApi';
import { listBlogPostsBySlug } from '../features/post/postApi';
import type { PageResponse, PostSummary } from '../features/post/types';
import { ApiRequestError } from '../lib/apiClient';
import { useHeroBlog } from '../lib/heroBlogContext';
import { useOptionalAuth } from '../lib/authContext';
import type { PublicBlogResponse } from '../features/settings/types';
import {
  acceptUniverseRequest,
  blockUniverseUser,
  cancelUniverseRequest,
  deleteUniverseRelationship,
  getRelationshipSnapshot,
  requestUniverse,
  unblockUniverseUser,
} from '../features/universe/universeApi';
import type { UniverseRelationshipSnapshot } from '../features/universe/types';

const PAGE_SIZE = 10;

function parsePage(value: string | null): number {
  const page = Number(value);
  return Number.isInteger(page) && page >= 0 ? page : 0;
}

function parseCategory(value: string | null): number | undefined {
  if (!value) return undefined;
  const categoryId = Number(value);
  return Number.isInteger(categoryId) && categoryId > 0 ? categoryId : undefined;
}

function formatDate(value: string | null): string {
  if (!value) return '작성일 미정';
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' }).format(new Date(value));
}

function listErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.status === 403) return '이 글 목록을 볼 권한이 없습니다.';
    if (error.status === 404) return '블로그 글 목록을 찾을 수 없습니다.';
    return error.message || '글 목록을 불러오지 못했습니다.';
  }
  return '글 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
}

function blogErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError && error.status === 404) return '블로그를 찾을 수 없습니다.';
  return '블로그를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
}

function emptyPage(): PageResponse<PostSummary> {
  return {
    items: [],
    page: 0,
    size: PAGE_SIZE,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
  };
}

type RelationshipAction = 'request' | 'accept' | 'cancel' | 'delete' | 'block' | 'unblock';

function relationshipActionLabel(snapshot: UniverseRelationshipSnapshot): string {
  if (snapshot.availability === 'BLOCKED_BY_ME') return '차단 해제';
  if (snapshot.availability === 'UNAVAILABLE') return '관계 이용 불가';
  if (snapshot.incoming.status === 'PENDING') return '신청 수락 ✦';
  if (snapshot.outgoing.status === 'PENDING') return '신청 취소';
  if (snapshot.outgoing.status === 'ACCEPTED') {
    return snapshot.incoming.status === 'ACCEPTED' ? '친구 · 연결 끊기' : '연결 끊기';
  }
  return '✦ 유니버스 신청';
}

function relationshipActionKind(snapshot: UniverseRelationshipSnapshot): RelationshipAction | null {
  if (snapshot.availability === 'BLOCKED_BY_ME') return 'unblock';
  if (snapshot.availability === 'UNAVAILABLE') return null;
  if (snapshot.incoming.status === 'PENDING') return 'accept';
  if (snapshot.outgoing.status === 'PENDING') return 'cancel';
  if (snapshot.outgoing.status === 'ACCEPTED') return 'delete';
  return 'request';
}

/** 공개 slug 블로그의 실제 게시글 목록과 URL 필터를 연결한다. */
export function BlogPage() {
  const { blogSlug } = useParams<{ blogSlug: string }>();
  const { setBlog: setHeroBlog, setActions: setHeroActions } = useHeroBlog();
  const auth = useOptionalAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [blog, setBlog] = useState<PublicBlogResponse | null>(null);
  const [posts, setPosts] = useState<PageResponse<PostSummary>>(emptyPage);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [listWarning, setListWarning] = useState<string | null>(null);
  const [canRetry, setCanRetry] = useState(false);
  const [retryKey, setRetryKey] = useState(0);
  const [relationshipSnapshot, setRelationshipSnapshot] = useState<UniverseRelationshipSnapshot | null>(null);
  const [relationshipNotice, setRelationshipNotice] = useState<string | null>(null);
  const [relationshipBusy, setRelationshipBusy] = useState(false);
  const [relationshipRetryKey, setRelationshipRetryKey] = useState(0);

  // React Router may return a new URLSearchParams wrapper on a parent render. Use its
  // serialized value as the dependency so a state update from the load effect does not
  // start the same request loop again.
  const searchParamsKey = searchParams.toString();
  const filters = useMemo(() => {
    const params = new URLSearchParams(searchParamsKey);
    const rawSort = params.get('sort');
    return {
      page: parsePage(params.get('page')),
      categoryId: parseCategory(params.get('categoryId')),
      tag: params.get('tag')?.trim() || undefined,
      sort: rawSort === 'popular' ? ('popular' as const) : ('latest' as const),
    };
  }, [searchParamsKey]);

  useEffect(() => {
    let cancelled = false;
    if (!blogSlug) return () => {
      cancelled = true;
    };

    setIsLoading(true);
    setError(null);
    setListWarning(null);
    setCanRetry(false);
    setBlog(null);
    setPosts(emptyPage());
    setHeroBlog(null);
    setHeroActions(null);

    const load = async () => {
      try {
        const loadedBlog = await getPublicBlog(blogSlug);
        if (cancelled) return;
        if (loadedBlog.urlSlug !== blogSlug) {
          setError('요청한 블로그 주소와 응답이 일치하지 않습니다.');
          return;
        }
        setBlog(loadedBlog);
        setHeroBlog(loadedBlog);

        try {
          const loadedPosts = await listBlogPostsBySlug(blogSlug, { ...filters, size: PAGE_SIZE });
          if (cancelled) return;
          if (!loadedPosts || !Array.isArray(loadedPosts.items)) {
            throw new Error('글 목록 응답 형식이 올바르지 않습니다.');
          }
          setPosts(loadedPosts);
        } catch (listError) {
          if (cancelled) return;
          setPosts(emptyPage());
          if (listError instanceof ApiRequestError) {
            setListWarning(listErrorMessage(listError));
          }
        }
      } catch (loadError) {
        if (cancelled) return;
        setBlog(null);
        setPosts(emptyPage());
        setHeroBlog(null);
        setHeroActions(null);
        setError(blogErrorMessage(loadError));
        setCanRetry(!(loadError instanceof ApiRequestError) || loadError.status !== 404);
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };
    void load();

    return () => {
      cancelled = true;
      setHeroBlog(null);
      setHeroActions(null);
    };
  }, [auth?.user?.id, blogSlug, filters, retryKey, setHeroActions, setHeroBlog]);

  /** 공개 블로그 조회와 보호 snapshot 조회를 분리해 guest 공개 화면을 깨뜨리지 않는다. */
  useEffect(() => {
    const viewerId = auth?.user?.id;
    const ownerId = blog?.owner?.id;
    if (!blog || viewerId == null || ownerId == null || viewerId === ownerId) {
      setRelationshipSnapshot(null);
      setRelationshipNotice(null);
      return;
    }

    let cancelled = false;
    const targetId = ownerId;
    setRelationshipSnapshot(null);
    setRelationshipNotice(null);
    void getRelationshipSnapshot(targetId)
      .then((snapshot) => {
        if (!cancelled) setRelationshipSnapshot(snapshot);
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setRelationshipSnapshot(null);
        if (error instanceof ApiRequestError && error.status === 401) {
          setRelationshipNotice('로그인이 만료되었습니다. 다시 접속해 주세요.');
        } else {
          setRelationshipNotice('관계 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.');
        }
      });

    return () => {
      cancelled = true;
    };
  }, [auth?.user?.id, blog?.owner?.id, blog?.id, relationshipRetryKey]);

  const runRelationshipAction = async (action: RelationshipAction) => {
    if (!relationshipSnapshot || !blog || relationshipBusy) return;
    const targetId = relationshipSnapshot.target.id;
    const requestId = relationshipSnapshot.outgoing.status === 'PENDING'
      ? relationshipSnapshot.outgoing.requestId
      : relationshipSnapshot.incoming.requestId;
    setRelationshipBusy(true);
    setRelationshipNotice(null);
    try {
      if (action === 'request') await requestUniverse(targetId);
      if (action === 'accept' && relationshipSnapshot.incoming.requestId != null) {
        await acceptUniverseRequest(relationshipSnapshot.incoming.requestId);
      }
      if (action === 'cancel' && requestId != null) await cancelUniverseRequest(requestId);
      if (action === 'delete') await deleteUniverseRelationship(targetId);
      if (action === 'block') await blockUniverseUser(targetId);
      if (action === 'unblock') await unblockUniverseUser(targetId);
      setRelationshipRetryKey((key) => key + 1);
      setRelationshipNotice('관계 상태를 반영했습니다.');
    } catch (error) {
      if (error instanceof ApiRequestError && (error.code === 'UNI_005' || error.status === 409)) {
        setRelationshipRetryKey((key) => key + 1);
        setRelationshipNotice('관계 상태가 이미 바뀌었습니다. 최신 상태를 불러왔습니다.');
      } else {
        setRelationshipNotice(
          error instanceof ApiRequestError ? error.message : '관계 요청을 처리하지 못했습니다.',
        );
      }
    } finally {
      setRelationshipBusy(false);
    }
  };

  useEffect(() => {
    if (!blog) {
      setHeroActions(null);
      return;
    }
    if (auth?.user?.id == null) {
      setHeroActions(
        <Link
          to="/signin"
          className="border-[3px] border-ink bg-surface px-4 py-2 text-xs font-bold text-ink shadow-[4px_4px_0_rgba(43,27,61,.5)]"
        >
          ✦ 유니버스 신청
        </Link>,
      );
      return;
    }
    if (auth.user.id === blog.owner?.id) {
      setHeroActions(
        <>
          <Link
            to="/settings"
            className="border-[3px] border-ink bg-surface px-4 py-2 text-xs font-bold text-ink shadow-[4px_4px_0_rgba(43,27,61,.5)]"
          >
            블로그 관리
          </Link>
          <Link
            to="/write"
            className="border-[3px] border-ink bg-accent px-4 py-2 text-xs font-bold text-white shadow-[4px_4px_0_rgba(43,27,61,.5)]"
          >
            새 글 작성
          </Link>
        </>,
      );
      return;
    }
    if (!relationshipSnapshot) {
      setHeroActions(
        <span className="border-2 border-ink bg-surface px-4 py-2 text-xs text-text-muted shadow-[4px_4px_0_rgba(43,27,61,.5)]">
          {relationshipNotice ?? '관계 확인 중...'}
        </span>,
      );
      return;
    }
    const action = relationshipActionKind(relationshipSnapshot);
    if (action === null) {
      setHeroActions(
        <Button size="sm" variant="inert" className="px-4 py-2 text-xs" disabled>
          관계 이용 불가
        </Button>,
      );
      return;
    }
    setHeroActions(
      <Button
        size="sm"
        variant={action === 'unblock' ? 'danger' : 'neutral'}
        className="px-4 py-2 text-xs"
        style={{ boxShadow: '4px 4px 0 rgba(43,27,61,.5)' }}
        disabled={relationshipBusy}
        onClick={() => void runRelationshipAction(action)}
      >
        {relationshipBusy ? '처리 중...' : relationshipActionLabel(relationshipSnapshot)}
      </Button>,
    );
  }, [
    auth?.user?.id,
    blog,
    relationshipBusy,
    relationshipNotice,
    relationshipSnapshot,
    setHeroActions,
  ]);

  const updateFilter = (key: 'sort' | 'categoryId' | 'tag', value: string) => {
    const next = new URLSearchParams(searchParams);
    if (!value) next.delete(key);
    else next.set(key, value);
    next.set('page', '0');
    setSearchParams(next);
  };

  const setPage = (page: number) => {
    const next = new URLSearchParams(searchParams);
    next.set('page', String(page));
    setSearchParams(next);
  };

  if (isLoading) {
    return (
      <div className="border-[3px] border-ink bg-surface px-6 py-16 text-center shadow-card">
        <p className="text-[13px] text-text-muted">글 목록을 불러오는 중...</p>
      </div>
    );
  }

  if (error || !blog) {
    return (
      <div className="border-[3px] border-ink bg-surface px-6 py-16 text-center shadow-card">
        <p role="alert" className="text-[13px] text-danger">
          {error ?? '블로그 정보를 불러올 수 없습니다.'}
        </p>
        {canRetry && (
          <Button
            variant="neutral"
            size="sm"
            className="mt-4"
            onClick={() => setRetryKey((key) => key + 1)}
          >
            다시 시도
          </Button>
        )}
      </div>
    );
  }

  const categoryLabel = filters.categoryId ? `카테고리 ${filters.categoryId}` : '전체 글';

  return (
    <section aria-label={`${blog.title} 글 목록`}>
      <div className="mb-[18px] flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="font-pixel text-[10px] tracking-[2px] text-ink">PUBLIC LOGS_</p>
          <h1 className="mt-2 text-[22px] font-bold text-ink">{categoryLabel} · {posts.totalElements}개의 글</h1>
        </div>
        <div className="flex items-center gap-2 text-[13px]">
          <label htmlFor="post-sort" className="text-text-muted">정렬</label>
          <select
            id="post-sort"
            aria-label="정렬"
            value={filters.sort}
            onChange={(event) => updateFilter('sort', event.target.value)}
            className="border-2 border-ink bg-surface px-2 py-1.5"
          >
            <option value="latest">최신순</option>
            <option value="popular">인기순</option>
          </select>
          <input
            aria-label="태그 필터"
            defaultValue={filters.tag ?? ''}
            key={filters.tag ?? 'empty-tag'}
            placeholder="#태그"
            className="w-[120px] border-2 border-ink bg-surface px-2 py-1.5"
            onKeyDown={(event) => {
              if (event.key === 'Enter') updateFilter('tag', event.currentTarget.value.trim());
            }}
          />
        </div>
      </div>

      {listWarning && <p role="alert" className="mb-3 border-2 border-danger bg-danger-bg px-3 py-2 text-xs text-danger">{listWarning}</p>}

      {posts.items.length === 0 ? (
        <div className="border-[3px] border-ink bg-surface px-6 py-16 text-center shadow-card">
          <p className="text-[13px] text-text-muted">아직 발행된 글이 없습니다.</p>
          <p className="mt-2 text-xs text-text-muted">공개된 로그가 생기면 이곳에 표시됩니다.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-[14px]">
          {posts.items.map((post) => (
            <Link key={post.id} to={`/blog/${post.blogSlug}/${post.id}`} className="block">
              <PostCard
                title={post.title || '제목 없는 초안'}
                meta={`${blog.title}/${post.category.name} · ${formatDate(post.publishedAt)}`}
                excerpt={post.excerpt}
                visibility={post.visibility}
                tags={post.tags}
                thumbnailUrl={post.thumbnailUrl}
              />
            </Link>
          ))}
        </div>
      )}

      {(posts.hasPrevious || posts.hasNext) && (
        <nav aria-label="글 목록 페이지" className="mt-5 flex items-center justify-center gap-2">
          <button
            type="button"
            aria-label="이전 페이지"
            disabled={!posts.hasPrevious}
            onClick={() => setPage(filters.page - 1)}
            className="border-2 border-ink bg-surface px-3 py-1.5 text-xs font-bold disabled:cursor-not-allowed disabled:opacity-40"
          >
            ← 이전
          </button>
          <span className="text-xs text-text-muted">{posts.page + 1} / {posts.totalPages}</span>
          <button
            type="button"
            aria-label="다음 페이지"
            disabled={!posts.hasNext}
            onClick={() => setPage(filters.page + 1)}
            className="border-2 border-ink bg-surface px-3 py-1.5 text-xs font-bold disabled:cursor-not-allowed disabled:opacity-40"
          >
            다음 →
          </button>
        </nav>
      )}
    </section>
  );
}
