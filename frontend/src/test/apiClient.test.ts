import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  apiClient,
  ApiRequestError,
  resetApiClient,
  setAccessToken,
  setAuthExpiredHandler,
} from '../lib/apiClient';

/**
 * apiClient 401 자동 갱신(PRD §8.2).
 *
 * <p>핵심은 **single-flight** — 동시에 여러 요청이 401을 받아도 refresh는 한 번만 돌아야 한다.
 * 각자 갱신하면 rotation 때문에 뒤늦은 요청이 이미 폐기된 토큰을 쓰게 된다.
 */

function envelope(data: unknown, ok = true, error: unknown = null) {
  return {
    ok,
    status: ok ? 200 : 401,
    text: async () =>
      JSON.stringify({ success: ok, data, error, timestamp: '2026-07-25T00:00:00Z' }),
    json: async () => ({ success: ok, data, error, timestamp: '2026-07-25T00:00:00Z' }),
  } as unknown as Response;
}

function failure(status: number, code: string, message = '실패', details: unknown[] = []) {
  return {
    ok: false,
    status,
    text: async () =>
      JSON.stringify({
        success: false,
        data: null,
        error: { code, message, details },
        timestamp: '2026-07-25T00:00:00Z',
      }),
    json: async () => ({
      success: false,
      data: null,
      error: { code, message, details },
      timestamp: '2026-07-25T00:00:00Z',
    }),
  } as unknown as Response;
}

describe('apiClient', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    resetApiClient();
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it('성공 응답에서 data만 벗겨 돌려준다', async () => {
    fetchMock.mockResolvedValueOnce(envelope({ id: 1 }));

    await expect(apiClient.get('/api/v1/auth/me')).resolves.toEqual({ id: 1 });
  });

  it('모든 요청에 credentials:include를 붙인다 (Refresh 쿠키 전송)', async () => {
    fetchMock.mockResolvedValueOnce(envelope(null));

    await apiClient.get('/api/v1/auth/me');

    expect(fetchMock.mock.calls[0][1]).toMatchObject({ credentials: 'include' });
  });

  it('Access Token이 있을 때만 Bearer를 붙인다', async () => {
    fetchMock.mockResolvedValue(envelope(null));

    await apiClient.get('/api/v1/auth/me');
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();

    setAccessToken('token-1');
    await apiClient.get('/api/v1/auth/me');
    expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe('Bearer token-1');
  });

  it('실패 응답은 서버 code를 그대로 담은 ApiRequestError로 던진다', async () => {
    fetchMock.mockResolvedValueOnce(failure(409, 'USER_004', '이미 사용 중인 이메일입니다.'));

    await expect(apiClient.post('/api/v1/auth/register', {})).rejects.toSatisfy(
      (error: unknown) =>
        error instanceof ApiRequestError &&
        error.code === 'USER_004' &&
        error.status === 409,
    );
  });

  it('검증 실패의 필드 사유를 꺼낼 수 있다', async () => {
    fetchMock.mockResolvedValueOnce(
      failure(400, 'VALIDATION_001', '요청 값이 올바르지 않습니다.', [
        { field: 'password', reason: '비밀번호는 영문·숫자·특수문자를 모두 포함해야 합니다.' },
      ]),
    );

    try {
      await apiClient.post('/api/v1/auth/register', {});
      expect.unreachable('던져야 한다');
    } catch (error) {
      expect(error).toBeInstanceOf(ApiRequestError);
      expect((error as ApiRequestError).fieldError('password')).toContain('특수문자');
    }
  });

  describe('401 자동 갱신', () => {
    it('401이면 refresh 후 원래 요청을 1회 재시도한다', async () => {
      setAccessToken('old-token');
      fetchMock
        .mockResolvedValueOnce(failure(401, 'AUTH_002')) // 최초 요청
        .mockResolvedValueOnce(envelope({ accessToken: 'new-token' })) // refresh
        .mockResolvedValueOnce(envelope({ id: 1 })); // 재시도

      await expect(apiClient.get('/api/v1/auth/me')).resolves.toEqual({ id: 1 });

      expect(fetchMock).toHaveBeenCalledTimes(3);
      expect(fetchMock.mock.calls[1][0]).toContain('/api/v1/auth/refresh');
      // 재시도는 새 토큰을 쓴다.
      expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe('Bearer new-token');
    });

    it('동시 401 세 건이어도 refresh는 한 번만 돈다 (single-flight)', async () => {
      setAccessToken('old-token');

      let refreshCount = 0;
      fetchMock.mockImplementation(async (url: string) => {
        if (url.includes('/auth/refresh')) {
          refreshCount += 1;
          await new Promise((resolve) => setTimeout(resolve, 10));
          return envelope({ accessToken: 'new-token' });
        }
        // 새 토큰이 준비되기 전 요청은 401.
        return refreshCount === 0 ? failure(401, 'AUTH_002') : envelope({ ok: true });
      });

      await Promise.all([
        apiClient.get('/api/v1/posts/drafts'),
        apiClient.get('/api/v1/notifications'),
        apiClient.get('/api/v1/auth/me'),
      ]);

      expect(refreshCount).toBe(1);
    });

    it('인증 엔드포인트의 401은 refresh를 시도하지 않는다 (재귀 방지)', async () => {
      fetchMock.mockResolvedValueOnce(failure(401, 'AUTH_001'));

      await expect(
        apiClient.post('/api/v1/auth/signin', { email: 'a@b.c', password: 'x' }),
      ).rejects.toBeInstanceOf(ApiRequestError);

      expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('refresh가 실패하면 만료 핸들러를 부르고 토큰을 지운다', async () => {
      setAccessToken('old-token');
      const onExpired = vi.fn();
      setAuthExpiredHandler(onExpired);

      fetchMock
        .mockResolvedValueOnce(failure(401, 'AUTH_002'))
        .mockResolvedValueOnce(failure(401, 'AUTH_003')); // refresh 실패

      await expect(apiClient.get('/api/v1/auth/me')).rejects.toBeInstanceOf(ApiRequestError);

      expect(onExpired).toHaveBeenCalledTimes(1);
      expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it('옛 토큰으로 동시 출발한 요청 중 하나의 401이 지연돼도 refresh는 1회다', async () => {
      setAccessToken('old-token');

      let refreshCount = 0;
      let releaseSlow401: (() => void) | null = null;
      const slow401 = new Promise<void>((resolve) => {
        releaseSlow401 = resolve;
      });

      fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
        const auth = (init.headers as Record<string, string> | undefined)?.Authorization;

        if (url.includes('/auth/refresh')) {
          refreshCount += 1;
          return envelope({ accessToken: 'new-token' });
        }
        if (auth === 'Bearer new-token') {
          return envelope({ ok: true });
        }
        // 옛 토큰 요청. 느린 쪽은 refresh가 끝난 뒤에야 401을 돌려준다.
        if (url.includes('/notifications')) {
          await slow401;
        }
        return failure(401, 'AUTH_002');
      });

      // 두 요청 모두 옛 토큰으로 출발한다.
      const fast = apiClient.get('/api/v1/posts/drafts');
      const slow = apiClient.get('/api/v1/notifications');

      // 빠른 쪽이 401 → refresh → 재시도까지 끝낸다.
      await expect(fast).resolves.toEqual({ ok: true });
      expect(refreshCount).toBe(1);

      // 이제 느린 쪽의 401이 도착한다. 토큰은 이미 바뀐 뒤다.
      releaseSlow401!();
      await expect(slow).resolves.toEqual({ ok: true });

      // 지연된 401이 두 번째 rotation을 일으키면 안 된다.
      expect(refreshCount).toBe(1);
    });

    it('재시도까지 401이면 만료로 처리하고 더 재시도하지 않는다', async () => {
      setAccessToken('old-token');
      const onExpired = vi.fn();
      setAuthExpiredHandler(onExpired);

      fetchMock
        .mockResolvedValueOnce(failure(401, 'AUTH_002'))
        .mockResolvedValueOnce(envelope({ accessToken: 'new-token' }))
        .mockResolvedValueOnce(failure(401, 'AUTH_004')); // 재시도도 실패

      await expect(apiClient.get('/api/v1/auth/me')).rejects.toBeInstanceOf(ApiRequestError);

      expect(onExpired).toHaveBeenCalledTimes(1);
      expect(fetchMock).toHaveBeenCalledTimes(3);
    });
  });
});
