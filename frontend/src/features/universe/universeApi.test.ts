import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetApiClient, setAccessToken } from '../../lib/apiClient';
import {
  acceptUniverseRequest,
  blockUniverseUser,
  cancelUniverseRequest,
  deleteUniverseRelationship,
  getRelationshipSnapshot,
  listUniverse,
  rejectUniverseRequest,
  requestUniverse,
  unblockUniverseUser,
} from './universeApi';
import type { UniverseRelationshipSnapshot, UniverseRequestItem } from './types';

function envelope<T>(data: T) {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ success: true, data, error: null, timestamp: '' }),
    json: async () => ({ success: true, data, error: null, timestamp: '' }),
  } as unknown as Response;
}

const USER = {
  id: 7,
  nickname: '별사람',
  profileImageUrl: null,
  blogSlug: 'star-person',
};

const REQUEST: UniverseRequestItem = {
  requestId: 99,
  user: USER,
  createdAt: '2026-11-07T00:00:00Z',
};

const SNAPSHOT: UniverseRelationshipSnapshot = {
  target: USER,
  availability: 'AVAILABLE',
  outgoing: { status: 'PENDING', requestId: 99 },
  incoming: { status: 'NONE', requestId: null },
};

describe('universeApi', () => {
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

  it('신청/목록/snapshot 경로와 query를 정본 계약대로 호출한다', async () => {
    fetchMock
      .mockResolvedValueOnce(envelope(REQUEST))
      .mockResolvedValueOnce(
        envelope({
          items: [REQUEST],
          page: 1,
          size: 20,
          totalElements: 21,
          totalPages: 2,
          hasNext: false,
          hasPrevious: true,
        }),
      )
      .mockResolvedValueOnce(envelope(SNAPSHOT));

    await expect(requestUniverse(7)).resolves.toEqual(REQUEST);
    await expect(listUniverse('received', { nickname: '별 사람', page: 1, size: 20 })).resolves.toMatchObject({
      page: 1,
      items: [REQUEST],
    });
    await expect(getRelationshipSnapshot(7)).resolves.toEqual(SNAPSHOT);

    expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/api/v1/universe/requests/7');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(fetchMock.mock.calls[1][0]).toBe(
      'http://localhost:8080/api/v1/universe/requests/received?page=1&size=20&nickname=%EB%B3%84+%EC%82%AC%EB%9E%8C',
    );
    expect(fetchMock.mock.calls[2][0]).toBe(
      'http://localhost:8080/api/v1/universe/relationship/7',
    );
  });

  it('신청 수락과 관계/차단 mutation은 계약된 method/path를 사용한다', async () => {
    await acceptUniverseRequest(99);
    await rejectUniverseRequest(99);
    await cancelUniverseRequest(99);
    await deleteUniverseRelationship(7);
    await blockUniverseUser(7);
    await unblockUniverseUser(7);

    expect(fetchMock.mock.calls.map(([url, init]) => [url, init.method])).toEqual([
      ['http://localhost:8080/api/v1/universe/requests/99/accept', 'POST'],
      ['http://localhost:8080/api/v1/universe/requests/99/reject', 'POST'],
      ['http://localhost:8080/api/v1/universe/requests/99', 'DELETE'],
      ['http://localhost:8080/api/v1/universe/7', 'DELETE'],
      ['http://localhost:8080/api/v1/universe/blocks/7', 'POST'],
      ['http://localhost:8080/api/v1/universe/blocks/7', 'DELETE'],
    ]);
  });

  it.each([
    ['sent', '/api/v1/universe/requests/sent'],
    ['following', '/api/v1/universe/following'],
    ['followers', '/api/v1/universe/followers'],
    ['friends', '/api/v1/universe/friends'],
    ['blocks', '/api/v1/universe/blocks'],
  ] as const)('%s 목록은 page/size 기본값과 nickname 필터를 보낸다', async (kind, path) => {
    fetchMock.mockResolvedValueOnce(
      envelope({
        items: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
        hasPrevious: false,
      }),
    );
    await listUniverse(kind, { nickname: 'neo' });
    expect(fetchMock.mock.calls[0][0]).toBe(
      `http://localhost:8080${path}?page=0&size=20&nickname=neo`,
    );
  });

  it('size는 서버 최대값 100을 넘기지 않는다', async () => {
    fetchMock.mockResolvedValueOnce(
      envelope({
        items: [],
        page: 0,
        size: 100,
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
        hasPrevious: false,
      }),
    );

    await listUniverse('friends', { page: 3, size: 120 });

    expect(fetchMock.mock.calls[0][0]).toBe(
      'http://localhost:8080/api/v1/universe/friends?page=3&size=100',
    );
  });
});
