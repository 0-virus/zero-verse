import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { apiClient, setAccessToken, getAccessToken } from './apiClient'

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
})

describe('apiClient request', () => {
  const okJson = (body: unknown) => ({
    ok: true,
    status: 200,
    json: async () => body,
  })

  beforeEach(() => {
    setAccessToken(null)
    vi.restoreAllMocks()
  })

  afterEach(() => {
    setAccessToken(null)
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
})
