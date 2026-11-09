import { apiClient } from '../../lib/apiClient';
import type { FeedPage, PublicFeedParams, UniverseFeedParams } from './types';

export const MAX_DISCOVERY_KEYWORD_UTF16 = 100;

/** 서버 계약과 같은 trim·연속 whitespace 정규화. JS length는 UTF-16 code unit이다. */
export function normalizeDiscoveryKeyword(value: string): string {
  const normalized = value.trim().replace(/\s+/gu, ' ');
  if (normalized.length > MAX_DISCOVERY_KEYWORD_UTF16) {
    throw new Error('검색어는 UTF-16 기준 100자 이하로 입력해 주세요.');
  }
  return normalized;
}

function requireData<T>(value: T | null, label: string): T {
  if (value === null) throw new Error(`${label} 응답이 비어 있습니다.`);
  return value;
}

function query(params: PublicFeedParams): string {
  const result = new URLSearchParams();
  const keyword = params.keyword == null ? '' : normalizeDiscoveryKeyword(params.keyword);
  if (keyword) result.set('keyword', keyword);
  result.set('page', String(params.page ?? 0));
  result.set('size', String(params.size ?? 20));
  result.set('sort', params.sort ?? 'latest');
  return result.toString();
}

export async function getPublicFeed(params: PublicFeedParams = {}): Promise<FeedPage> {
  return requireData(
    await apiClient.get<FeedPage>(`/api/v1/feed/public?${query(params)}`),
    '공개 피드',
  );
}

export async function getUniverseFeed(params: UniverseFeedParams): Promise<FeedPage> {
  const { relation, ...rest } = params;
  return requireData(
    await apiClient.get<FeedPage>(
      `/api/v1/feed/universe?relation=${encodeURIComponent(relation)}&${query(rest)}`,
    ),
    '유니버스 피드',
  );
}
