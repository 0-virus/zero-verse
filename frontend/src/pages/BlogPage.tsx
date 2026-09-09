import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { getPublicBlog } from '../features/blog/blogApi';
import { ApiRequestError } from '../lib/apiClient';
import { useHeroBlog } from '../lib/heroBlogContext';
import type { PublicBlogResponse } from '../features/settings/types';

/**
 * `/blog/:blogSlug` 블로그 화면.
 *
 * 히어로(`AppShell`)가 실시간 title/description/owner를 표시한다.
 * 좌측 패널은 `ScreenPanel`이 이 페이지가 채운 공개 블로그 context의 카테고리·통계를 표시한다(M3).
 * 본문은 글 목록 또는 빈 상태를 표시한다(M4 미구현).
 */
export function BlogPage() {
  const { blogSlug } = useParams<{ blogSlug: string }>();
  const { setBlog: setHeroBlog } = useHeroBlog();
  const [blog, setBlog] = useState<PublicBlogResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [canRetry, setCanRetry] = useState(false);
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    // 이 effect가 낡았는지 표시한다.
    //
    // `/blog/A` → `/blog/B`로 옮기면 A 요청이 아직 떠 있다. cleanup은 히어로를 비울 뿐
    // 이미 날아간 요청을 멈추지 못하므로, 늦게 도착한 A 성공이 B 화면을 A로 되돌리고
    // 늦은 A 실패는 멀쩡한 B 화면을 404로 바꾼다. 낡은 응답은 아무것도 반영하지 않는다.
    let cancelled = false;

    const loadBlog = async () => {
      if (!blogSlug) return;

      setIsLoading(true);
      setError(null);
      setCanRetry(false);

      try {
        const data = await getPublicBlog(blogSlug);
        if (cancelled) return;
        setBlog(data);
        setHeroBlog(data);
      } catch (err: unknown) {
        if (cancelled) return;
        if (err instanceof ApiRequestError && err.status === 404) {
          setError('블로그를 찾을 수 없습니다.');
          setCanRetry(false);
        } else {
          setError('블로그를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
          setCanRetry(true);
        }
        setBlog(null);
        setHeroBlog(null);
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };

    loadBlog();

    // cleanup: 페이지 나갈 때 히어로 정보 초기화 + 진행 중 요청 무효화
    return () => {
      cancelled = true;
      setHeroBlog(null);
    };
  }, [blogSlug, retryKey, setHeroBlog]);

  if (isLoading) {
    return (
      <div className="border-[3px] border-ink bg-surface px-6 py-16 text-center shadow-card">
        <p className="text-[13px] text-text-muted">블로그를 불러오는 중...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="border-[3px] border-ink bg-surface px-6 py-16 text-center shadow-card">
        <p role="alert" className="text-[13px] text-danger">
          {error}
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

  if (!blog) {
    return (
      <div className="border-[3px] border-ink bg-surface px-6 py-16 text-center shadow-card">
        <p className="text-[13px] text-text-muted">블로그 정보를 불러올 수 없습니다.</p>
      </div>
    );
  }

  return (
    <div className="border-[3px] border-ink bg-surface px-6 py-16 text-center shadow-card">
      <p className="text-[13px] text-text-muted">아직 발행된 글이 없습니다.</p>
    </div>
  );
}
