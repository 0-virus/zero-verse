import { apiClient } from '../../lib/apiClient';
import type {
  CreatePostRequest,
  PageResponse,
  PostDetail,
  PostListParams,
  PostSummary,
  UpdatePostImagesRequest,
  UpdatePostRequest,
} from './types';

function requireData<T>(response: T | null, label: string): T {
  if (response === null) throw new Error(`${label} 응답이 비어 있습니다.`);
  return response;
}

function pageQuery(params: PostListParams = {}): string {
  const query = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 20),
  });
  if (params.sort) query.set('sort', params.sort);
  if (params.categoryId !== undefined) query.set('categoryId', String(params.categoryId));
  if (params.tag) query.set('tag', params.tag);
  if (params.visibility) query.set('visibility', params.visibility);
  if (params.publish !== undefined) query.set('publish', String(params.publish));
  return query.toString();
}

export async function createPost(request: CreatePostRequest): Promise<PostDetail> {
  return requireData(await apiClient.post<PostDetail>('/api/v1/posts', request), '글 작성');
}

export async function updatePost(postId: number, request: UpdatePostRequest): Promise<PostDetail> {
  return requireData(await apiClient.put<PostDetail>(`/api/v1/posts/${postId}`, request), '글 수정');
}

export async function getPost(postId: number): Promise<PostDetail> {
  return requireData(await apiClient.get<PostDetail>(`/api/v1/posts/${postId}`), '글 상세');
}

export async function listBlogPosts(
  blogId: number,
  params: PostListParams = {},
): Promise<PageResponse<PostSummary>> {
  return requireData(
    await apiClient.get<PageResponse<PostSummary>>(
      `/api/v1/blogs/${blogId}/posts?${pageQuery(params)}`,
    ),
    '글 목록',
  );
}

/** 공개 블로그 화면이 owner 여부와 무관하게 사용할 수 있는 slug 목록 경로. */
export async function listBlogPostsBySlug(
  blogSlug: string,
  params: PostListParams = {},
): Promise<PageResponse<PostSummary>> {
  return requireData(
    await apiClient.get<PageResponse<PostSummary>>(
      `/api/v1/blogs/slug/${encodeURIComponent(blogSlug)}/posts?${pageQuery(params)}`,
    ),
    '공개 글 목록',
  );
}

export async function listDrafts(
  params: Pick<PostListParams, 'page' | 'size'> = {},
): Promise<PageResponse<PostSummary>> {
  return requireData(
    await apiClient.get<PageResponse<PostSummary>>(`/api/v1/posts/drafts?${pageQuery(params)}`),
    '임시저장 목록',
  );
}

export async function listTagPosts(
  tagName: string,
  params: PostListParams = {},
): Promise<PageResponse<PostSummary>> {
  return requireData(
    await apiClient.get<PageResponse<PostSummary>>(
      `/api/v1/tags/${encodeURIComponent(tagName)}/posts?${pageQuery(params)}`,
    ),
    '태그 글 목록',
  );
}

export async function deletePost(postId: number): Promise<null> {
  await apiClient.delete<null>(`/api/v1/posts/${postId}`);
  return null;
}

export async function updatePostImages(
  postId: number,
  request: UpdatePostImagesRequest,
): Promise<PostDetail['images']> {
  return requireData(
    await apiClient.put<PostDetail['images']>(
      `/api/v1/posts/${postId}/images`,
      request,
    ),
    '글 이미지 수정',
  );
}
