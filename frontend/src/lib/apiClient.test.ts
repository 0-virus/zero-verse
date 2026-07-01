import { describe, it, expect } from 'vitest'
import { setAccessToken, getAccessToken } from './apiClient'

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
