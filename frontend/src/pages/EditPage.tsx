import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { getAllCategories, flattenCategories, type Category } from '../features/category/categoryApi';
import { PostEditor } from '../features/post/PostEditor';
import { getPost, updatePost } from '../features/post/postApi';
import type { CreatePostRequest, PostDetail, UpdatePostRequest } from '../features/post/types';
import { ApiRequestError } from '../lib/apiClient';
import { useAuth } from '../lib/authContext';

function categoriesForEditor(categories: Category[]) {
  return flattenCategories(categories).map(({ id, name, type }) => ({ id, name, type }));
}

function loadErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.status === 403) return '이 글을 수정할 권한이 없습니다.';
    if (error.status === 404) return '수정할 글을 찾을 수 없습니다.';
    return error.message || '글을 불러오지 못했습니다.';
  }
  return '글을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
}

export function EditPage() {
  const { postId: rawPostId } = useParams<{ postId: string }>();
  const postId = Number(rawPostId);
  const { user } = useAuth();
  const navigate = useNavigate();
  const blogId = user?.defaultBlog.id ?? null;
  const userId = user?.id ?? null;
  const currentUserIdRef = useRef(userId);
  currentUserIdRef.current = userId;
  const [post, setPost] = useState<PostDetail | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    if (!Number.isInteger(postId) || postId <= 0 || blogId == null || userId == null) {
      setPost(null);
      setCategories([]);
      setIsLoading(false);
      setError('수정할 글 주소 또는 로그인한 블로그가 올바르지 않습니다.');
      return () => {
        cancelled = true;
      };
    }
    setIsLoading(true);
    setError(null);
    void Promise.all([getPost(postId), getAllCategories(blogId, true)])
      .then(([loadedPost, loadedCategories]) => {
        if (cancelled) return;
        if (loadedPost.blogId !== blogId || loadedPost.author.id !== userId) {
          setPost(null);
          setError('이 글을 수정할 권한이 없습니다.');
          return;
        }
        setPost(loadedPost);
        setCategories(loadedCategories);
      })
      .catch((loadError: unknown) => {
        if (!cancelled) {
          setPost(null);
          setError(loadErrorMessage(loadError));
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [blogId, postId, userId]);

  const save = useCallback(async (request: UpdatePostRequest | CreatePostRequest): Promise<PostDetail> => {
    if ('blogId' in request) {
      throw new Error('기존 글 수정 요청에는 블로그가 포함될 수 없습니다.');
    }
    const requestUserId = userId;
    const saved = await updatePost(postId, request);
    if (currentUserIdRef.current !== requestUserId) {
      throw new Error('세션이 변경되어 저장 결과를 적용하지 않았습니다.');
    }
    if (request.publish) navigate(`/blog/${saved.blogSlug}/${saved.id}`);
    return saved;
  }, [navigate, postId, userId]);

  if (isLoading) return <p className="border-[3px] border-ink bg-surface px-6 py-16 text-center text-sm shadow-card">글을 불러오는 중...</p>;
  if (error || !post || blogId == null) {
    return <p role="alert" className="border-[3px] border-ink bg-surface px-6 py-16 text-center text-sm text-danger shadow-card">{error ?? '글을 찾을 수 없습니다.'}</p>;
  }

  return (
    <PostEditor
      mode="edit"
      blogId={blogId}
      postId={post.id}
      initialPost={post}
      categories={categoriesForEditor(categories)}
      onSave={save}
      onDraftSelect={(draft) => navigate(`/edit/${draft.id}`)}
    />
  );
}
