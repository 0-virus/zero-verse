import { apiClient } from '../../lib/apiClient';
import type {
  AdminRole,
  AdminStatus,
  AdminUserDetail,
  AdminUserListParams,
  AdminUserListResponse,
} from './types';

export const MAX_ADMIN_KEYWORD_UTF16 = 100;
export const DEFAULT_ADMIN_PAGE_SIZE = 20;
export const MAX_ADMIN_PAGE_SIZE = 100;

function requireData<T>(value: T | null, label: string): T {
  if (value === null) throw new Error(`${label} 응답이 비어 있습니다.`);
  return value;
}

function requireId(id: number): number {
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error('사용자 ID가 올바르지 않습니다.');
  return id;
}

function requirePage(page: number | undefined): number {
  const value = page ?? 0;
  if (!Number.isSafeInteger(value) || value < 0) throw new Error('페이지는 0 이상이어야 합니다.');
  return value;
}

function requireSize(size: number | undefined): number {
  const value = size ?? DEFAULT_ADMIN_PAGE_SIZE;
  if (!Number.isSafeInteger(value) || value < 1 || value > MAX_ADMIN_PAGE_SIZE) {
    throw new Error(`페이지 크기는 1~${MAX_ADMIN_PAGE_SIZE}이어야 합니다.`);
  }
  return value;
}

export function normalizeAdminKeyword(keyword: string | undefined): string {
  const value = (keyword ?? '').trim().replace(/\s+/gu, ' ');
  if (value.length > MAX_ADMIN_KEYWORD_UTF16) {
    throw new Error('검색어는 UTF-16 기준 100자 이하로 입력해 주세요.');
  }
  return value;
}

function query(params: AdminUserListParams = {}): string {
  const search = new URLSearchParams();
  const keyword = normalizeAdminKeyword(params.keyword);
  const page = requirePage(params.page);
  const size = requireSize(params.size);
  if (keyword) search.set('keyword', keyword);
  if (params.role !== undefined) {
    if (params.role !== 'USER' && params.role !== 'ADMIN') throw new Error('역할이 올바르지 않습니다.');
    search.set('role', params.role);
  }
  if (params.status !== undefined) {
    if (params.status !== 'ACTIVE' && params.status !== 'SUSPENDED') throw new Error('상태가 올바르지 않습니다.');
    search.set('status', params.status);
  }
  search.set('page', String(page));
  search.set('size', String(size));
  return search.toString();
}

async function list(params: AdminUserListParams = {}): Promise<AdminUserListResponse> {
  return requireData(
    await apiClient.get<AdminUserListResponse>(`/api/v1/admin/users?${query(params)}`),
    '관리자 사용자 목록',
  );
}

async function get(userId: number): Promise<AdminUserDetail> {
  return requireData(
    await apiClient.get<AdminUserDetail>(`/api/v1/admin/users/${requireId(userId)}`),
    '관리자 사용자 상세',
  );
}

async function changeRole(userId: number, role: AdminRole): Promise<AdminUserDetail> {
  if (role !== 'USER' && role !== 'ADMIN') throw new Error('역할이 올바르지 않습니다.');
  return requireData(
    await apiClient.put<AdminUserDetail>(`/api/v1/admin/users/${requireId(userId)}/role`, { role }),
    '역할 변경',
  );
}

async function changeStatus(userId: number, status: AdminStatus): Promise<AdminUserDetail> {
  if (status !== 'ACTIVE' && status !== 'SUSPENDED') throw new Error('상태가 올바르지 않습니다.');
  return requireData(
    await apiClient.put<AdminUserDetail>(`/api/v1/admin/users/${requireId(userId)}/status`, { status }),
    '계정 상태 변경',
  );
}

async function deleteUser(userId: number): Promise<null> {
  return apiClient.delete<null>(`/api/v1/admin/users/${requireId(userId)}`);
}

export const adminApi = { list, get, changeRole, changeStatus, delete: deleteUser };

export { changeRole, changeStatus, deleteUser as delete, get, list };
