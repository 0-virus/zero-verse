import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminApi } from '../features/admin/adminApi';
import { resetApiClient, setAccessToken } from '../lib/apiClient';

function ok(data: unknown) {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ success: true, data, error: null, timestamp: '' }),
  } as unknown as Response;
}

describe('adminApi', () => {
  beforeEach(() => {
    resetApiClient();
    setAccessToken('admin-token');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it('목록은 trim된 keyword와 고정 paging만 query로 보낸다', async () => {
    const fetchMock = vi.fn(async (_url: string, _options?: RequestInit) => ok({ users: { items: [] }, summary: { totalUsers: 0, joinedThisWeek: 0, suspendedUsers: 0 } }));
    vi.stubGlobal('fetch', fetchMock);

    await adminApi.list({ keyword: '  orbit%_\\  ', page: 2, size: 1 });

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/admin/users?keyword=orbit%25_%5C&page=2&size=1',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('목록 query는 양끝 공백만 제거하고 내부 두 공백을 보존한다', async () => {
    const fetchMock = vi.fn(async (_url: string, _options?: RequestInit) => ok({ users: { items: [] }, summary: { totalUsers: 0, joinedThisWeek: 0, suspendedUsers: 0 } }));
    vi.stubGlobal('fetch', fetchMock);

    await adminApi.list({ keyword: '  Kim  Lee  ', page: 0, size: 20 });

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/admin/users?keyword=Kim%20%20Lee&page=0&size=20',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('역할·상태 변경은 승인된 endpoint와 payload를 사용한다', async () => {
    const fetchMock = vi.fn(async (_url: string, _options?: RequestInit) => ok({ id: 7 }));
    vi.stubGlobal('fetch', fetchMock);

    await adminApi.changeRole(7, 'ADMIN');
    await adminApi.changeStatus(7, 'SUSPENDED');
    await adminApi.delete(7);

    const calls = fetchMock.mock.calls as Array<[string, RequestInit?]>;
    expect(calls[0]?.[0]).toBe('http://localhost:8080/api/v1/admin/users/7/role');
    expect(JSON.parse(String(calls[0]?.[1]?.body))).toEqual({ role: 'ADMIN' });
    expect(calls[1]?.[0]).toBe('http://localhost:8080/api/v1/admin/users/7/status');
    expect(JSON.parse(String(calls[1]?.[1]?.body))).toEqual({ status: 'SUSPENDED' });
    expect(calls[2]?.[0]).toBe('http://localhost:8080/api/v1/admin/users/7');
    expect(calls[2]?.[1]).toEqual(expect.objectContaining({ method: 'DELETE' }));
  });

  it('잘못된 page/size와 100자를 넘는 keyword를 호출 전에 거부한다', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    await expect(adminApi.list({ page: -1 })).rejects.toThrow('페이지');
    await expect(adminApi.list({ size: 101 })).rejects.toThrow('페이지 크기');
    await expect(adminApi.list({ keyword: 'x'.repeat(101) })).rejects.toThrow('검색어');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
