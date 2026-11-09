import { apiClient } from '../../lib/apiClient';
import type { InteractionState } from './types';

function requireData<T>(response: T | null, label: string): T {
  if (response === null) throw new Error(`${label} 응답이 비어 있습니다.`);
  return response;
}

export async function getInteractions(postId: number): Promise<InteractionState> {
  return requireData(
    await apiClient.get<InteractionState>(`/api/v1/posts/${postId}/interactions`),
    '상호작용 상태',
  );
}

export async function likePost(postId: number): Promise<InteractionState> {
  return requireData(
    await apiClient.post<InteractionState>(`/api/v1/posts/${postId}/likes`),
    '좋아요',
  );
}

export async function unlikePost(postId: number): Promise<InteractionState> {
  return requireData(
    await apiClient.delete<InteractionState>(`/api/v1/posts/${postId}/likes`),
    '좋아요 취소',
  );
}
