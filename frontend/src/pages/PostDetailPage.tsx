import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Badge } from '../components/ui/Badge';
import { PostContent, Prose } from '../components/ui/Prose';
import { TagChip } from '../components/ui/TagChip';
import { deletePost, getPost } from '../features/post/postApi';
import { CommentSection } from '../features/comment/CommentSection';
import { getInteractions, likePost, unlikePost } from '../features/like/likeApi';
import type { InteractionState } from '../features/like/types';
import type { PostDetail } from '../features/post/types';
import { ManagedImage } from '../features/upload/ManagedImage';
import { ApiRequestError } from '../lib/apiClient';
import { useOptionalAuth } from '../lib/authContext';

function readTime(contentHtml: string): number {
  const text = contentHtml.replace(/<[^>]+>/g, ' ').replace(/&nbsp;/g, ' ').trim();
  if (!text) return 1;
  return Math.max(1, Math.ceil(Array.from(text).length / 400));
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  );
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.status === 403) return '이 글을 볼 권한이 없습니다.';
    if (error.status === 404) return '글을 찾을 수 없습니다.';
    return error.message || '글을 불러오지 못했습니다.';
  }
  return '글을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
}

function interactionErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.status === 401) return '로그인 후 좋아요를 누를 수 있습니다.';
    if (error.code === 'POST_001') return '글을 찾을 수 없습니다.';
    if (error.code === 'POST_002') {
      return '이 글을 볼 권한이 없습니다.';
    }
    if (error.code === 'LIKE_001') {
      return '현재 상태에서는 좋아요를 사용할 수 없습니다.';
    }
    return error.message || '상호작용 상태를 불러오지 못했습니다.';
  }
  return '상호작용 상태를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
}

function isReadAccessError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError
    && (error.code === 'AUTH_004' || error.code === 'POST_001' || error.code === 'POST_002');
}

function likeMutationErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.status === 401) return '로그인 후 좋아요를 누를 수 있습니다.';
    if (error.code === 'LIKE_001' || error.code === 'POST_002') {
      return '현재 상태에서는 좋아요를 사용할 수 없습니다.';
    }
  }
  return '좋아요를 처리하지 못했습니다.';
}

export function PostDetailPage() {
  const { blogSlug, postId: rawPostId } = useParams<{ blogSlug: string; postId: string }>();
  const postId = Number(rawPostId);
  const auth = useOptionalAuth();
  const viewer = auth?.user ?? null;
  const viewerId = viewer?.id ?? null;
  const navigate = useNavigate();
  const [post, setPost] = useState<PostDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [shareNotice, setShareNotice] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [interaction, setInteraction] = useState<InteractionState | null>(null);
  const [interactionLoading, setInteractionLoading] = useState(false);
  const [interactionError, setInteractionError] = useState<string | null>(null);
  const [interactionAccessDenied, setInteractionAccessDenied] = useState(false);
  const [interactionRetryable, setInteractionRetryable] = useState(false);
  const [isLikeBusy, setIsLikeBusy] = useState(false);
  const pageGeneration = useRef(0);

  useEffect(() => {
    const generation = ++pageGeneration.current;
    let cancelled = false;
    if (!blogSlug || !Number.isInteger(postId) || postId <= 0) {
      setError('잘못된 글 주소입니다.');
      setIsLoading(false);
      return () => {
        cancelled = true;
      };
    }

    setIsLoading(true);
    setError(null);
    setPost(null);
    setInteraction(null);
    setInteractionError(null);
    setInteractionLoading(false);
    setInteractionAccessDenied(false);
    setInteractionRetryable(false);
    setIsLikeBusy(false);
    void getPost(postId)
      .then((loaded) => {
        if (cancelled || pageGeneration.current !== generation) return;
        if (loaded.blogSlug !== blogSlug) {
          setError('요청한 블로그 주소와 글이 일치하지 않습니다.');
          return;
        }
        setPost(loaded);
      })
      .catch((loadError: unknown) => {
        if (!cancelled && pageGeneration.current === generation) setError(errorMessage(loadError));
      })
      .finally(() => {
        if (!cancelled && pageGeneration.current === generation) setIsLoading(false);
      });

    return () => {
      cancelled = true;
      if (pageGeneration.current === generation) pageGeneration.current += 1;
    };
  }, [blogSlug, postId, viewerId]);

  useEffect(() => {
    if (!post) return undefined;
    const generation = pageGeneration.current;
    let cancelled = false;
    setInteractionLoading(true);
    setInteractionError(null);
    void getInteractions(post.id)
      .then((loaded) => {
        if (!cancelled && pageGeneration.current === generation) {
          setInteraction(loaded);
          setInteractionAccessDenied(false);
          setInteractionRetryable(false);
        }
      })
      .catch((loadError: unknown) => {
        if (cancelled || pageGeneration.current !== generation) return;
        setInteraction(null);
        const accessDenied = isReadAccessError(loadError);
        setInteractionAccessDenied(accessDenied);
        setInteractionRetryable(!accessDenied);
        setInteractionError(interactionErrorMessage(loadError));
      })
      .finally(() => {
        if (!cancelled && pageGeneration.current === generation) setInteractionLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [post?.id, viewerId]);

  const owner = post !== null && viewerId === post.author.id;
  const minutes = useMemo(() => (post ? readTime(post.contentHtml) : 1), [post]);

  const refreshInteractions = useCallback(async () => {
    if (!post) return;
    const generation = pageGeneration.current;
    try {
      const loaded = await getInteractions(post.id);
      if (pageGeneration.current === generation) {
        setInteraction(loaded);
        setInteractionAccessDenied(false);
        setInteractionRetryable(false);
        setInteractionError(null);
      }
    } catch (loadError) {
      if (pageGeneration.current === generation) {
        const accessDenied = isReadAccessError(loadError);
        if (accessDenied) {
          setInteraction(null);
          setInteractionAccessDenied(true);
        }
        setInteractionRetryable(!accessDenied);
        setInteractionError(interactionErrorMessage(loadError));
      }
      throw loadError;
    }
  }, [post]);

  const retryInteractions = useCallback(async () => {
    try {
      await refreshInteractions();
    } catch {
      // refreshInteractions owns the error and retry state shown to the user.
    }
  }, [refreshInteractions]);

  const handleInteractionAccessDenied = useCallback((loadError: unknown) => {
    setInteraction(null);
    const accessDenied = isReadAccessError(loadError);
    setInteractionAccessDenied(accessDenied);
    setInteractionRetryable(false);
    setInteractionError(interactionErrorMessage(loadError));
  }, []);

  const toggleLike = async () => {
    if (!post || !interaction || isLikeBusy) return;
    if (viewerId == null) {
      navigate('/signin');
      return;
    }
    const nextLiked = !interaction.liked;
    if (nextLiked && !interaction.canLike) {
      setInteractionError('현재 상태에서는 좋아요를 사용할 수 없습니다.');
      return;
    }
    if (!nextLiked && !interaction.canUnlike) {
      setInteractionError('현재 상태에서는 좋아요를 취소할 수 없습니다.');
      return;
    }
    const generation = pageGeneration.current;
    const previous = interaction;
    setInteraction({
      ...interaction,
      liked: nextLiked,
      likeCount: Math.max(0, interaction.likeCount + (nextLiked ? 1 : -1)),
      canLike: nextLiked ? false : interaction.canLike,
      canUnlike: nextLiked ? true : interaction.canUnlike,
    });
    setIsLikeBusy(true);
    setInteractionError(null);
    setInteractionRetryable(false);
    try {
      const canonical = nextLiked ? await likePost(post.id) : await unlikePost(post.id);
      if (pageGeneration.current === generation) {
        setInteraction(canonical);
        setInteractionRetryable(false);
      }
    } catch (likeError) {
      if (pageGeneration.current !== generation) return;
      if (isReadAccessError(likeError)) {
        setInteraction(null);
        setInteractionAccessDenied(true);
        setInteractionRetryable(false);
        setInteractionError(interactionErrorMessage(likeError));
      } else if (likeError instanceof ApiRequestError && likeError.code === 'LIKE_001') {
        try {
          const canonical = await getInteractions(post.id);
          if (pageGeneration.current !== generation) return;
          setInteraction(canonical);
          setInteractionAccessDenied(false);
          setInteractionRetryable(false);
          setInteractionError(likeMutationErrorMessage(likeError));
        } catch (recheckError) {
          if (pageGeneration.current !== generation) return;
          if (isReadAccessError(recheckError)) {
            setInteraction(null);
            setInteractionAccessDenied(true);
            setInteractionRetryable(false);
            setInteractionError(interactionErrorMessage(recheckError));
          } else {
            setInteraction(previous);
            setInteractionRetryable(true);
            setInteractionError('좋아요 상태를 다시 확인하지 못했습니다. 현재 상태를 유지합니다.');
          }
        }
      } else {
        setInteraction(previous);
        setInteractionRetryable(false);
        setInteractionError(likeMutationErrorMessage(likeError));
      }
    } finally {
      if (pageGeneration.current === generation) setIsLikeBusy(false);
    }
  };

  const share = async () => {
    try {
      if (!window.navigator.clipboard?.writeText) throw new Error('clipboard unavailable');
      await window.navigator.clipboard.writeText(window.location.href);
      setShareNotice('링크를 복사했습니다.');
    } catch {
      setShareNotice('주소를 복사하지 못했습니다. 브라우저 주소를 확인해 주세요.');
    }
    window.setTimeout(() => setShareNotice(null), 3000);
  };

  const remove = async () => {
    if (!post || isDeleting || !window.confirm('이 글을 삭제할까요?')) return;
    setIsDeleting(true);
    try {
      await deletePost(post.id);
      navigate(`/blog/${post.blogSlug}`, { replace: true });
    } catch (deleteError) {
      setError(errorMessage(deleteError));
      setIsDeleting(false);
    }
  };

  if (isLoading) {
    return <p className="border-[3px] border-ink bg-surface px-6 py-16 text-center text-sm shadow-card">글을 불러오는 중...</p>;
  }
  if (error || !post) {
    return (
      <div className="border-[3px] border-ink bg-surface px-6 py-16 text-center shadow-card">
        <p role="alert" className="text-sm text-danger">{error ?? '글을 찾을 수 없습니다.'}</p>
      </div>
    );
  }

  return (
    <article className="mx-auto max-w-[980px]">
      <nav aria-label="브레드크럼" className="mb-4 text-xs text-text-muted">
        <Link to={`/blog/${post.blogSlug}`} className="font-semibold text-ink hover:text-accent">
          {post.blogTitle}
        </Link>
        <span className="px-2">›</span>
        <span>{post.category.name}</span>
      </nav>

      <section className="border-[3px] border-ink bg-surface shadow-panel">
        <header className="border-b-[3px] border-ink bg-[linear-gradient(180deg,#fff8ec,#fff)] px-[34px] py-7">
          <div className="mb-3 flex items-center gap-2">
            <Badge kind={post.visibility} />
            <span className="font-pixel text-[8px] tracking-[2px] text-universe">
              LOG #{String(post.id).padStart(3, '0')}
            </span>
          </div>
          <h1 className="mb-3 text-[27px] font-bold leading-[1.4] text-ink">{post.title}</h1>
          <div className="flex items-center gap-2.5 text-[12.5px] text-text-muted">
            <div className="grid h-[26px] w-[26px] shrink-0 place-items-center border-2 border-ink bg-surface-raise">
              {post.author.profileImageUrl ? (
                <ManagedImage
                  src={post.author.profileImageUrl}
                  alt={`${post.author.nickname}의 프로필 이미지`}
                  className="h-full w-full object-cover"
                />
              ) : (
                <span role="img" aria-label={`${post.author.nickname}의 아바타`} className="text-[13px]">🪐</span>
              )}
            </div>
            <strong className="text-ink">{post.author.nickname}</strong>
            <span>· {formatDate(post.publishedAt ?? post.createdAt)} · 읽는 데 {minutes}분 · 조회 {post.viewCount}</span>
            <div className="flex-1" />
            {post.tags.map((tag) => <TagChip key={tag} name={tag} />)}
          </div>
        </header>

        <Prose>
          <PostContent content={post.contentJson} />
        </Prose>

        <footer className="flex flex-wrap items-center gap-2 border-t-[3px] border-ink bg-surface-warm px-[34px] py-[18px]">
          {viewerId == null && interaction ? (
            <Link
              to="/signin"
              className="border-[3px] border-ink bg-surface px-[18px] py-2 text-[13px] font-bold shadow-btn"
            >
              ♥ 좋아요 {interaction.likeCount}
            </Link>
          ) : viewerId != null && interaction ? (
            <button
              type="button"
              aria-pressed={interaction?.liked ?? false}
              onClick={() => void toggleLike()}
              disabled={isLikeBusy || !interaction || (!interaction.liked && !interaction.canLike) || (interaction.liked && !interaction.canUnlike)}
              className={`border-[3px] border-ink px-[18px] py-2 text-[13px] font-bold shadow-btn disabled:cursor-not-allowed disabled:opacity-60 ${interaction?.liked ? 'bg-accent text-white' : 'bg-surface text-ink'}`}
            >
              ♥ 좋아요 {interaction.likeCount}
            </button>
          ) : (
            <span className="border-2 border-shadow bg-surface-inert px-[18px] py-2 text-[13px] font-bold text-text-muted">
              좋아요를 확인할 수 없습니다.
            </span>
          )}
          <button
            type="button"
            onClick={() => void share()}
            className="border-[3px] border-ink bg-surface px-[18px] py-2 text-[13px] font-bold shadow-btn"
          >
            공유
          </button>
          {owner && (
            <>
              <Link to={`/edit/${post.id}`} className="border-[3px] border-ink bg-surface px-[18px] py-2 text-[13px] font-bold shadow-btn">수정</Link>
              <button
                type="button"
                onClick={() => void remove()}
                disabled={isDeleting}
                className="border-2 border-danger bg-danger-bg px-3 py-1.5 text-xs font-bold text-danger disabled:opacity-50"
              >
                {isDeleting ? '삭제 중...' : '삭제'}
              </button>
            </>
          )}
          <div className="flex-1" />
          {post.previous && (
            <Link to={`/blog/${post.previous.blogSlug}/${post.previous.id}`} className="border-[3px] border-shadow bg-surface px-[18px] py-2 text-[13px] font-bold text-text-muted">
              ← 이전 글
            </Link>
          )}
          {post.next && (
            <Link to={`/blog/${post.next.blogSlug}/${post.next.id}`} className="border-[3px] border-ink bg-surface px-[18px] py-2 text-[13px] font-bold shadow-btn">
              다음 글 →
            </Link>
          )}
        </footer>
      </section>

      {interactionError && (
        <div role="alert" className="mt-3 flex items-center justify-between gap-3 border-2 border-danger bg-danger-bg px-4 py-2 text-xs text-danger">
          <span>{interactionError}</span>
          {interactionRetryable && (
            <button
              type="button"
              onClick={() => void retryInteractions()}
              className="cursor-pointer font-bold underline underline-offset-2"
            >
              다시 시도
            </button>
          )}
        </div>
      )}
      {!interactionAccessDenied && (
        <CommentSection
          key={`${post.id}:${viewerId ?? 'guest'}`}
          postId={post.id}
          viewer={viewer}
          interaction={interaction}
          interactionLoading={interactionLoading}
          onRefreshInteractions={refreshInteractions}
          onInteractionAccessDenied={handleInteractionAccessDenied}
        />
      )}

      {shareNotice && <p role="status" className="mt-3 border-2 border-success bg-success-bg px-4 py-2 text-xs text-success">{shareNotice}</p>}
    </article>
  );
}
