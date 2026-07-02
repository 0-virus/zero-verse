import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { apiClient, setAccessToken, getAccessToken, setOnUnauthorized } from './apiClient'

describe('apiClient token store', () => {
  it('should store and retrieve access token', () => {
    const testToken = 'test-token-12345'

    setAccessToken(testToken)
    expect(getAccessToken()).toBe(testToken)
  })

  it('should clear token when set to null', () => {
    setAccessToken('some-token')
    setAccessToken(null)
    expect(getAccessToken()).toBeNull()
  })

  it('should synchronize token between contexts', () => {
    const token1 = 'token-1'
    const token2 = 'token-2'

    setAccessToken(token1)
    expect(getAccessToken()).toBe(token1)

    setAccessToken(token2)
    expect(getAccessToken()).toBe(token2)
  })

  it('should set and clear unauthorized callback', () => {
    const callback = vi.fn()
    setOnUnauthorized(callback)
    // Just verify it doesn't throw
    expect(() => setOnUnauthorized(null)).not.toThrow()
  })
})

describe('apiClient request', () => {
  const okJson = (body: unknown) => ({
    ok: true,
    status: 200,
    json: async () => body,
  })

  beforeEach(() => {
    setAccessToken(null)
    setOnUnauthorized(null)
    vi.restoreAllMocks()
  })

  afterEach(() => {
    setAccessToken(null)
    setOnUnauthorized(null)
  })

  it('attaches the Bearer header when an access token is set', async () => {
    setAccessToken('live-token')
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(okJson({ success: true, data: {}, error: null, timestamp: 'now' }) as unknown as Response)

    await apiClient('/auth/me')

    const [, init] = fetchMock.mock.calls[0]
    const headers = new Headers(init?.headers)
    expect(headers.get('Authorization')).toBe('Bearer live-token')
    expect(init?.credentials).toBe('include')
  })

  it('omits the Bearer header when no token is set', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(okJson({ success: true, data: {}, error: null, timestamp: 'now' }) as unknown as Response)

    await apiClient('/feed/public')

    const [, init] = fetchMock.mock.calls[0]
    const headers = new Headers(init?.headers)
    expect(headers.get('Authorization')).toBeNull()
  })

  it('refreshes on 401 then retries the original request with the new token', async () => {
    setAccessToken('stale-token')
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      // 1st: original request -> 401
      .mockResolvedValueOnce({ ok: false, status: 401, json: async () => ({}) } as unknown as Response)
      // 2nd: refresh -> issues a new token
      .mockResolvedValueOnce(
        okJson({ success: true, data: { accessToken: 'fresh-token' }, error: null, timestamp: 'now' }) as unknown as Response,
      )
      // 3rd: retried original request -> success
      .mockResolvedValueOnce(okJson({ success: true, data: { ok: true }, error: null, timestamp: 'now' }) as unknown as Response)

    const result = await apiClient('/posts/drafts')

    expect(fetchMock).toHaveBeenCalledTimes(3)
    const retryHeaders = new Headers(fetchMock.mock.calls[2][1]?.headers)
    expect(retryHeaders.get('Authorization')).toBe('Bearer fresh-token')
    expect(result.success).toBe(true)
    expect(getAccessToken()).toBe('fresh-token')
  })

  it('does not retry refresh endpoint itself on 401', async () => {
    setAccessToken('stale-token')
    vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce({
        ok: false,
        status: 401,
        json: async () => ({
          success: false,
          error: { code: 'UNAUTHORIZED', message: 'Invalid refresh token' },
          timestamp: 'now',
        }),
      } as unknown as Response)

    const result = await apiClient('/auth/refresh')

    // Result should indicate failed refresh (no retry occurred)
    expect(result.success).toBe(false)
  })

  it('does not trigger refresh for public auth endpoint signin on 401', async () => {
    setAccessToken('stale-token')
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      // Only 1 call: original signin request -> 401 (no refresh triggered)
      .mockResolvedValueOnce({
        ok: false,
        status: 401,
        json: async () => ({
          success: false,
          error: { code: 'AUTH_001', message: '이메일 또는 비밀번호가 올바르지 않습니다.' },
          timestamp: 'now',
        }),
      } as unknown as Response)

    const result = await apiClient('/auth/signin', {
      method: 'POST',
      body: JSON.stringify({ email: 'test@example.com', password: 'wrong' }),
    })

    // Should have only 1 call (no refresh, no retry)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(result.success).toBe(false)
  })

  it('does not trigger refresh for public auth endpoint register on 401', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      // Only 1 call: original register request -> 401 (no refresh triggered)
      .mockResolvedValueOnce({
        ok: false,
        status: 401,
        json: async () => ({
          success: false,
          error: { code: 'AUTH_004', message: '인증이 필요합니다.' },
          timestamp: 'now',
        }),
      } as unknown as Response)

    const result = await apiClient('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email: 'test@example.com', password: 'Password!123', nickname: 'test' }),
    })

    // Should have only 1 call (no refresh, no retry)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(result.success).toBe(false)
  })

  it('calls onUnauthorized callback when refresh fails', async () => {
    setAccessToken('stale-token')
    const onUnauthorizedMock = vi.fn()
    setOnUnauthorized(onUnauthorizedMock)

    vi
      .spyOn(globalThis, 'fetch')
      // 1st: original request -> 401
      .mockResolvedValueOnce({ ok: false, status: 401, json: async () => ({}) } as unknown as Response)
      // 2nd: refresh -> fails
      .mockResolvedValueOnce({ ok: false, status: 401, json: async () => ({}) } as unknown as Response)

    await apiClient('/posts/drafts')

    expect(onUnauthorizedMock).toHaveBeenCalled()
    expect(getAccessToken()).toBeNull()
  })

  it('implements single-flight refresh for concurrent 401s', async () => {
    setAccessToken('stale-token')
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      // 1st & 2nd: two original requests -> both 401
      .mockResolvedValueOnce({ ok: false, status: 401, json: async () => ({}) } as unknown as Response)
      .mockResolvedValueOnce({ ok: false, status: 401, json: async () => ({}) } as unknown as Response)
      // 3rd: one refresh call (single-flight)
      .mockResolvedValueOnce(
        okJson({ success: true, data: { accessToken: 'fresh-token' }, error: null, timestamp: 'now' }) as unknown as Response,
      )
      // 4th & 5th: both retried requests
      .mockResolvedValueOnce(okJson({ success: true, data: { ok: true }, error: null, timestamp: 'now' }) as unknown as Response)
      .mockResolvedValueOnce(okJson({ success: true, data: { ok: true }, error: null, timestamp: 'now' }) as unknown as Response)

    // Make two concurrent requests
    const [result1, result2] = await Promise.all([
      apiClient('/posts/drafts'),
      apiClient('/posts/published'),
    ])

    expect(result1.success).toBe(true)
    expect(result2.success).toBe(true)
    // Should be: 2 originals + 1 refresh + 2 retries = 5 calls
    expect(fetchMock).toHaveBeenCalledTimes(5)
  })
})
