import type { PageResponse } from '../post/types';

export interface CommentAuthor {
  id: number;
  nickname: string;
  profileImageUrl: string | null;
}

export interface CommentItem {
  id: number;
  postId: number;
  parentId: number | null;
  author: CommentAuthor | null;
  content: string | null;
  deleted: boolean;
  createdAt: string;
  updatedAt: string;
  canEdit: boolean;
  canDelete: boolean;
  canReply: boolean;
  replies: PageResponse<CommentItem> | null;
}

export interface CreateCommentRequest {
  content: string;
  parentId?: number | null;
}

export interface UpdateCommentRequest {
  content: string;
}
