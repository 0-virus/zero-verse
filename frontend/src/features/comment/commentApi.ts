import { apiClient } from '../../lib/apiClient';
import type { PageResponse } from '../post/types';
import type { CommentItem, CreateCommentRequest, UpdateCommentRequest } from './types';

function requireData<T>(response: T | null, label: string): T {
  if (response === null) throw new Error(`${label} 응답이 비어 있습니다.`);
  return response;
}

function pageQuery(page = 0, size = 20, parentId?: number): string {
  const query = new URLSearchParams({
    page: String(Math.max(0, page)),
    size: String(Math.min(Math.max(1, size), 100)),
  });
  if (parentId !== undefined) query.set('parentId', String(parentId));
  return query.toString();
}

export async function listComments(
  postId: number,
  params: { page?: number; size?: number; parentId?: number } = {},
): Promise<PageResponse<CommentItem>> {
  return requireData(
    await apiClient.get<PageResponse<CommentItem>>(
      `/api/v1/posts/${postId}/comments?${pageQuery(params.page, params.size, params.parentId)}`,
    ),
    '댓글 목록',
  );
}

export async function createComment(
  postId: number,
  request: CreateCommentRequest,
): Promise<CommentItem> {
  return requireData(
    await apiClient.post<CommentItem>(`/api/v1/posts/${postId}/comments`, request),
    '댓글 작성',
  );
}

export async function updateComment(
  commentId: number,
  request: UpdateCommentRequest,
): Promise<CommentItem> {
  return requireData(
    await apiClient.put<CommentItem>(`/api/v1/comments/${commentId}`, request),
    '댓글 수정',
  );
}

export async function deleteComment(commentId: number): Promise<null> {
  await apiClient.delete<null>(`/api/v1/comments/${commentId}`);
  return null;
}
