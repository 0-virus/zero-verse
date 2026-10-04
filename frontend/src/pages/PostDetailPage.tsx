import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Badge } from '../components/ui/Badge';
import { PostContent, Prose } from '../components/ui/Prose';
import { TagChip } from '../components/ui/TagChip';
import { deletePost, getPost } from '../features/post/postApi';
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

export function PostDetailPage() {
  const { blogSlug, postId: rawPostId } = useParams<{ blogSlug: string; postId: string }>();
  const postId = Number(rawPostId);
  const viewerId = useOptionalAuth()?.user?.id ?? null;
  const navigate = useNavigate();
  const [post, setPost] = useState<PostDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [shareNotice, setShareNotice] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
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
    void getPost(postId)
      .then((loaded) => {
        if (cancelled) return;
        if (loaded.blogSlug !== blogSlug) {
          setError('요청한 블로그 주소와 글이 일치하지 않습니다.');
          return;
        }
        setPost(loaded);
      })
      .catch((loadError: unknown) => {
        if (!cancelled) setError(errorMessage(loadError));
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [blogSlug, postId, viewerId]);

  const owner = post !== null && viewerId === post.author.id;
  const minutes = useMemo(() => (post ? readTime(post.contentHtml) : 1), [post]);

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
        <header className="border-b-[3px] border-ink px-[34px] py-6">
          <div className="flex items-center gap-2">
            <Badge kind={post.visibility} />
            <span className="font-pixel text-[10px] tracking-[2px] text-text-muted">
              LOG #{String(post.id).padStart(3, '0')}
            </span>
          </div>
          <h1 className="mt-3 text-[27px] font-bold leading-tight text-ink">{post.title}</h1>
          <div className="mt-4 flex items-center gap-3">
            <div className="grid h-11 w-11 shrink-0 place-items-center border-2 border-ink bg-surface-raise">
              {post.author.profileImageUrl ? (
                <ManagedImage
                  src={post.author.profileImageUrl}
                  alt={`${post.author.nickname}의 프로필 이미지`}
                  className="h-full w-full object-cover"
                />
              ) : (
                <span role="img" aria-label={`${post.author.nickname}의 아바타`} className="text-2xl">🪐</span>
              )}
            </div>
            <div className="text-xs text-text-muted">
              <p className="font-semibold text-ink">{post.author.nickname}</p>
              <p>{formatDate(post.publishedAt ?? post.createdAt)} · 읽는 데 {minutes}분 · 조회 {post.viewCount}</p>
            </div>
          </div>
          {post.tags.length > 0 && (
            <div className="mt-4 flex flex-wrap gap-1.5">
              {post.tags.map((tag) => <TagChip key={tag} name={tag} />)}
            </div>
          )}
        </header>

        <Prose>
          <PostContent content={post.contentJson} />
        </Prose>

        <footer className="flex flex-wrap items-center gap-2 border-t-[3px] border-ink bg-surface-warm px-[34px] py-4">
          <button
            type="button"
            onClick={() => void share()}
            className="border-2 border-ink bg-surface px-3 py-1.5 text-xs font-bold"
          >
            공유
          </button>
          {owner && (
            <>
              <Link to={`/edit/${post.id}`} className="border-2 border-ink bg-surface px-3 py-1.5 text-xs font-bold">수정</Link>
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
            <Link to={`/blog/${post.previous.blogSlug}/${post.previous.id}`} className="border-2 border-ink bg-surface px-3 py-1.5 text-xs font-bold">
              ← 이전 글
            </Link>
          )}
          {post.next && (
            <Link to={`/blog/${post.next.blogSlug}/${post.next.id}`} className="border-2 border-ink bg-surface px-3 py-1.5 text-xs font-bold">
              다음 글 →
            </Link>
          )}
        </footer>
      </section>

      {shareNotice && <p role="status" className="mt-3 border-2 border-success bg-success-bg px-4 py-2 text-xs text-success">{shareNotice}</p>}
    </article>
  );
}
