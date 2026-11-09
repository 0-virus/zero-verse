import { apiClient } from '../../lib/apiClient';
import type { SearchParams, SearchResponse } from './types';

export const MAX_SEARCH_KEYWORD_UTF16 = 100;

export function normalizeSearchKeyword(value: string): string {
  const normalized = value.trim().replace(/\s+/gu, ' ');
  if (normalized.length > MAX_SEARCH_KEYWORD_UTF16) {
    throw new Error('검색어는 UTF-16 기준 100자 이하로 입력해 주세요.');
  }
  return normalized;
}

function requireKeyword(value: string): string {
  const keyword = normalizeSearchKeyword(value);
  if (!keyword) throw new Error('검색어를 입력해 주세요.');
  return keyword;
}

function requireData<T>(value: T | null, label: string): T {
  if (value === null) throw new Error(`${label} 응답이 비어 있습니다.`);
  return value;
}

export async function search(params: SearchParams): Promise<SearchResponse> {
  const query = new URLSearchParams();
  query.set('keyword', requireKeyword(params.keyword));
  query.set('type', params.type ?? 'ALL');
  query.set('page', String(params.page ?? 0));
  query.set('size', String(params.size ?? 20));
  query.set('sort', params.sort ?? 'latest');
  return requireData(await apiClient.get<SearchResponse>(`/api/v1/search?${query.toString()}`), '검색');
}
