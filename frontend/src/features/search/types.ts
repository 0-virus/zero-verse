import type { FeedPostSummary } from '../feed/types';
import type { PageResponse } from '../post/types';

export type SearchType = 'ALL' | 'POST' | 'BLOG' | 'USER' | 'TAG';
export type SearchSort = 'latest' | 'popular';
export type MatchedType = Exclude<SearchType, 'ALL'>;

export interface SearchPostSummary extends FeedPostSummary {
  matchedTypes: MatchedType[];
}

export interface SearchCounts {
  ALL: number;
  POST: number;
  BLOG: number;
  USER: number;
  TAG: number;
}

export type SearchResponse = PageResponse<SearchPostSummary> & { counts: SearchCounts };

export interface SearchParams {
  keyword: string;
  type?: SearchType;
  page?: number;
  size?: number;
  sort?: SearchSort;
}
