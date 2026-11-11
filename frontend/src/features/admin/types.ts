import type { PageResponse } from '../post/types';

export type AdminRole = 'USER' | 'ADMIN';
export type AdminStatus = 'ACTIVE' | 'SUSPENDED';

export interface AdminBlogSummary {
  id: number;
  title: string;
  urlSlug: string;
}

export interface AdminUserSummary {
  id: number;
  email: string;
  name: string;
  nickname: string;
  role: AdminRole;
  status: AdminStatus;
  profileImageUrl: string | null;
  createdAt: string;
  updatedAt: string;
  defaultBlog: AdminBlogSummary | null;
  blogCount: number;
  postCount: number;
}

export interface AdminPostStats {
  total: number;
  published: number;
  draft: number;
}

export interface AdminUniverseStats {
  outgoingCount: number;
  incomingCount: number;
  connectionCount: number;
}

export interface AdminUserDetail extends AdminUserSummary {
  postStats: AdminPostStats;
  commentCount: number;
  universeStats: AdminUniverseStats;
  lastAccessAt: string | null;
}

export type AdminUserPage = PageResponse<AdminUserSummary>;

export interface AdminSummary {
  totalUsers: number;
  joinedThisWeek: number;
  suspendedUsers: number;
}

export interface AdminUserListResponse {
  users: AdminUserPage;
  summary: AdminSummary;
}

export interface AdminUserListParams {
  keyword?: string;
  role?: AdminRole;
  status?: AdminStatus;
  page?: number;
  size?: number;
}
