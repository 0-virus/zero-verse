import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetApiClient, setAccessToken } from '../../lib/apiClient';
import {
  createComment,
  deleteComment,
  listComments,
  updateComment,
} from './commentApi';
import type { CommentItem } from './types';

function envelope<T>(data: T, status = 200) {
  return {
    ok: true,
    status,
    text: async () => JSON.stringify({ success: true, data, error: null, timestamp: '' }),
    json: async () => ({ success: true, data, error: null, timestamp: '' }),
  } as unknown as Response;
}

const author = { id: 7, nickname: '별사람', profileImageUrl: null };
const comment: CommentItem = {
  id: 11,
  postId: 4,
  parentId: null,
  author,
  content: '따뜻한 신호',
  deleted: false,
  createdAt: '2026-11-09T00:00:00Z',
  updatedAt: '2026-11-09T00:00:00Z',
  canEdit: true,
  canDelete: true,
  canReply: true,
  replies: {
    items: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
  },
};

describe('commentApi', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    resetApiClient();
    setAccessToken('test-token');
    fetchMock = vi.fn().mockResolvedValue(envelope(null));
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it('root/reply 목록은 PageResponse query와 size 최대 100을 사용한다', async () => {
    const page = {
      items: [comment],
      page: 2,
      size: 100,
      totalElements: 101,
      totalPages: 2,
      hasNext: false,
      hasPrevious: true,
    };
    fetchMock.mockResolvedValueOnce(envelope(page)).mockResolvedValueOnce(envelope(page));

    await expect(listComments(4, { page: 2, size: 120 })).resolves.toEqual(page);
    await expect(listComments(4, { parentId: 11 })).resolves.toEqual(page);

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      'http://localhost:8080/api/v1/posts/4/comments?page=2&size=100',
      'http://localhost:8080/api/v1/posts/4/comments?page=0&size=20&parentId=11',
    ]);
  });

  it('댓글 create/update/delete는 계약 경로와 body를 사용한다', async () => {
    fetchMock
      .mockResolvedValueOnce(envelope(comment, 201))
      .mockResolvedValueOnce(envelope({ ...comment, content: '수정된 신호' }))
      .mockResolvedValueOnce(envelope(null));

    await expect(createComment(4, { content: '따뜻한 신호', parentId: null })).resolves.toEqual(comment);
    await expect(updateComment(11, { content: '수정된 신호' })).resolves.toMatchObject({ content: '수정된 신호' });
    await expect(deleteComment(11)).resolves.toBeNull();

    expect(fetchMock.mock.calls.map(([url, init]) => [url, init.method, init.body])).toEqual([
      ['http://localhost:8080/api/v1/posts/4/comments', 'POST', JSON.stringify({ content: '따뜻한 신호', parentId: null })],
      ['http://localhost:8080/api/v1/comments/11', 'PUT', JSON.stringify({ content: '수정된 신호' })],
      ['http://localhost:8080/api/v1/comments/11', 'DELETE', undefined],
    ]);
  });
});
