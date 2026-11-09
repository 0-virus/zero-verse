import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetApiClient, setAccessToken } from '../../lib/apiClient';
import { getInteractions, likePost, unlikePost } from './likeApi';
import type { InteractionState } from './types';

function envelope<T>(data: T) {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ success: true, data, error: null, timestamp: '' }),
    json: async () => ({ success: true, data, error: null, timestamp: '' }),
  } as unknown as Response;
}

const interactions: InteractionState = {
  postId: 4,
  commentCount: 3,
  likeCount: 8,
  liked: true,
  canComment: true,
  canLike: false,
  canUnlike: true,
};

describe('likeApi', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    resetApiClient();
    setAccessToken('test-token');
    fetchMock = vi.fn().mockResolvedValue(envelope(interactions));
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it('interactions 조회와 like/unlike mutation은 canonical 상태를 반환한다', async () => {
    await expect(getInteractions(4)).resolves.toEqual(interactions);
    await expect(likePost(4)).resolves.toEqual(interactions);
    await expect(unlikePost(4)).resolves.toEqual(interactions);

    expect(fetchMock.mock.calls.map(([url, init]) => [url, init.method])).toEqual([
      ['http://localhost:8080/api/v1/posts/4/interactions', 'GET'],
      ['http://localhost:8080/api/v1/posts/4/likes', 'POST'],
      ['http://localhost:8080/api/v1/posts/4/likes', 'DELETE'],
    ]);
  });
});
