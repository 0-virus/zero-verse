import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PostEditor } from '../features/post/PostEditor';
import { createPost } from '../features/post/postApi';
import type { CreatePostRequest, PostDetail, PostSummary, UpdatePostRequest } from '../features/post/types';
import { getAllCategories, flattenCategories, type Category } from '../features/category/categoryApi';
import { ApiRequestError } from '../lib/apiClient';
import { useAuth } from '../lib/authContext';

function categoriesForEditor(categories: Category[]) {
  return flattenCategories(categories).map(({ id, name, type }) => ({ id, name, type }));
}

function loadErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) return error.message || '카테고리를 불러오지 못했습니다.';
  return '카테고리를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
}

export function WritePage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const blogId = user?.defaultBlog.id ?? null;
  const userId = user?.id ?? null;
  const currentUserIdRef = useRef(userId);
  currentUserIdRef.current = userId;
  const [categories, setCategories] = useState<Category[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    if (blogId == null || userId == null) {
      setCategories([]);
      setIsLoading(false);
      setError('로그인한 블로그를 확인할 수 없습니다.');
      return () => {
        cancelled = true;
      };
    }
    setIsLoading(true);
    setError(null);
    void getAllCategories(blogId, true)
      .then((loaded) => {
        if (!cancelled) setCategories(loaded);
      })
      .catch((loadError: unknown) => {
        if (!cancelled) setError(loadErrorMessage(loadError));
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [blogId, userId]);

  const save = useCallback(async (request: CreatePostRequest | UpdatePostRequest): Promise<PostDetail> => {
    if (!('blogId' in request) || typeof request.blogId !== 'number') {
      throw new Error('새 글 저장 요청에 블로그가 없습니다.');
    }
    const requestUserId = userId;
    const saved = await createPost(request);
    if (currentUserIdRef.current !== requestUserId) {
      throw new Error('세션이 변경되어 저장 결과를 적용하지 않았습니다.');
    }
    if (request.publish) navigate(`/blog/${saved.blogSlug}/${saved.id}`);
    else navigate(`/edit/${saved.id}`, { replace: true });
    return saved;
  }, [navigate, userId]);

  if (isLoading) return <p className="border-[3px] border-ink bg-surface px-6 py-16 text-center text-sm shadow-card">작성 환경을 준비하는 중...</p>;
  if (error || blogId == null) {
    return <p role="alert" className="border-[3px] border-ink bg-surface px-6 py-16 text-center text-sm text-danger shadow-card">{error ?? '블로그를 확인할 수 없습니다.'}</p>;
  }

  return (
    <PostEditor
      mode="create"
      blogId={blogId}
      categories={categoriesForEditor(categories)}
      onSave={save}
      listDrafts={undefined}
      onDraftSelect={(draft: PostSummary) => navigate(`/edit/${draft.id}`)}
    />
  );
}
