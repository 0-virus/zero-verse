import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getPublicBlog } from '../features/blog/blogApi';
import { useHeroBlog } from '../lib/heroBlogContext';
import type { PublicBlogResponse } from '../features/settings/types';

/**
 * `/blog/:blogSlug` 블로그 화면.
 *
 * 히어로(`AppShell`)가 실시간 title/description/owner를 표시한다.
 * 좌측 패널은 `ScreenPanel`이 카테고리·통계를 표시한다(M3/M4 미구현).
 * 본문은 글 목록 또는 빈 상태를 표시한다(M4 미구현).
 */
export function BlogPage() {
  const { blogSlug } = useParams<{ blogSlug: string }>();
  const { setBlog: setHeroBlog } = useHeroBlog();
  const [blog, setBlog] = useState<PublicBlogResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadBlog = async () => {
      if (!blogSlug) return;

      setIsLoading(true);
      setError(null);

      try {
        const data = await getPublicBlog(blogSlug);
        setBlog(data);
        setHeroBlog(data);
      } catch {
        setError('블로그를 찾을 수 없습니다.');
        setBlog(null);
        setHeroBlog(null);
      } finally {
        setIsLoading(false);
      }
    };

    loadBlog();

    // cleanup: 페이지 나갈 때 히어로 정보 초기화
    return () => {
      setHeroBlog(null);
    };
  }, [blogSlug, setHeroBlog]);

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
        <p className="text-[13px] text-danger">{error}</p>
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
