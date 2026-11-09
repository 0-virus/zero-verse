import type { PageResponse, PostSummary } from '../post/types';

export type FeedRelation = 'all' | 'friends' | 'following';
export type DiscoverySort = 'latest' | 'popular';

/** M7 feed 전용 목록 요약. 기존 PostSummary/BlogPage 계약은 변경하지 않는다. */
export type FeedPostSummary = PostSummary & {
  commentCount: number;
  likeCount: number;
};

export type FeedPage = PageResponse<FeedPostSummary>;

export interface PublicFeedParams {
  keyword?: string;
  page?: number;
  size?: number;
  sort?: DiscoverySort;
}

export interface UniverseFeedParams extends PublicFeedParams {
  relation: FeedRelation;
}
