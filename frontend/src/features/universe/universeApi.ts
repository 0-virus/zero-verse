import { apiClient } from '../../lib/apiClient';
import type {
  UniverseListKind,
  UniverseListPage,
  UniverseListParams,
  UniverseRelationshipSnapshot,
  UniverseRequestItem,
} from './types';

function requireData<T>(response: T | null, label: string): T {
  if (response === null) throw new Error(`${label} 응답이 비어 있습니다.`);
  return response;
}

function pageQuery(params: UniverseListParams = {}): string {
  const query = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(Math.min(params.size ?? 20, 100)),
  });
  const nickname = params.nickname?.trim();
  if (nickname) query.set('nickname', nickname);
  return query.toString();
}

const LIST_PATH: Record<UniverseListKind, string> = {
  received: '/api/v1/universe/requests/received',
  sent: '/api/v1/universe/requests/sent',
  following: '/api/v1/universe/following',
  followers: '/api/v1/universe/followers',
  friends: '/api/v1/universe/friends',
  blocks: '/api/v1/universe/blocks',
};

export async function requestUniverse(targetUserId: number): Promise<UniverseRequestItem> {
  return requireData(
    await apiClient.post<UniverseRequestItem>(`/api/v1/universe/requests/${targetUserId}`),
    '유니버스 신청',
  );
}

export async function listUniverse(
  kind: UniverseListKind,
  params: UniverseListParams = {},
): Promise<UniverseListPage> {
  return requireData(
    await apiClient.get<UniverseListPage>(`${LIST_PATH[kind]}?${pageQuery(params)}`),
    '유니버스 목록',
  );
}

export async function acceptUniverseRequest(
  requestId: number,
): Promise<UniverseRelationshipSnapshot | null> {
  return apiClient.post<UniverseRelationshipSnapshot | null>(
    `/api/v1/universe/requests/${requestId}/accept`,
  );
}

export async function rejectUniverseRequest(requestId: number): Promise<null> {
  return apiClient.post<null>(`/api/v1/universe/requests/${requestId}/reject`);
}

export async function cancelUniverseRequest(requestId: number): Promise<null> {
  return apiClient.delete<null>(`/api/v1/universe/requests/${requestId}`);
}

export async function deleteUniverseRelationship(targetUserId: number): Promise<null> {
  return apiClient.delete<null>(`/api/v1/universe/${targetUserId}`);
}

export async function blockUniverseUser(targetUserId: number): Promise<null> {
  return apiClient.post<null>(`/api/v1/universe/blocks/${targetUserId}`);
}

export async function unblockUniverseUser(targetUserId: number): Promise<null> {
  return apiClient.delete<null>(`/api/v1/universe/blocks/${targetUserId}`);
}

export async function getRelationshipSnapshot(
  targetUserId: number,
): Promise<UniverseRelationshipSnapshot> {
  return requireData(
    await apiClient.get<UniverseRelationshipSnapshot>(
      `/api/v1/universe/relationship/${targetUserId}`,
    ),
    '유니버스 관계 상태',
  );
}
