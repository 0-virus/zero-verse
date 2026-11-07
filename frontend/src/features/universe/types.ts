import type { PageResponse } from '../category/categoryApi';

/** 관계 목록과 snapshot에 공통으로 노출하는 최소 개인정보 요약. */
export interface UniverseUserSummary {
  id: number;
  nickname: string;
  profileImageUrl: string | null;
  blogSlug: string | null;
}

export type UniverseRequestStatus = 'NONE' | 'PENDING' | 'ACCEPTED';
export type UniverseAvailability = 'AVAILABLE' | 'BLOCKED_BY_ME' | 'UNAVAILABLE';
export type UniverseListKind =
  | 'received'
  | 'sent'
  | 'following'
  | 'followers'
  | 'friends'
  | 'blocks';

export interface UniverseRequestItem {
  requestId: number;
  user: UniverseUserSummary;
  createdAt: string;
}

export interface UniverseRelationItem {
  user: UniverseUserSummary;
  updatedAt: string;
}

export interface DirectionalRelation {
  status: UniverseRequestStatus;
  requestId: number | null;
}

export interface UniverseRelationshipSnapshot {
  target: UniverseUserSummary;
  availability: UniverseAvailability;
  outgoing: DirectionalRelation;
  incoming: DirectionalRelation;
}

export type UniverseListPage =
  | PageResponse<UniverseRequestItem>
  | PageResponse<UniverseRelationItem>;

export interface UniverseListParams {
  nickname?: string;
  page?: number;
  size?: number;
}
