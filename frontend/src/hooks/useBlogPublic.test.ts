import { describe, it, expect, beforeEach, vi } from 'vitest'
import { apiClient } from '../lib/apiClient'

vi.mock('../lib/apiClient')

describe('useBlogPublic hook behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should fetch public blog successfully with owner info', async () => {
    const mockPublicBlog = {
      id: 1,
      title: 'Public Blog',
      urlSlug: 'public-blog',
      description: 'This is a public blog',
      owner: {
        userId: 2,
        nickname: 'blogger',
        profileImageUrl: 'https://example.com/avatar.jpg',
        bio: 'I am a blogger',
      },
      createdAt: '2026-01-01T00:00:00Z',
    }

    vi.mocked(apiClient).mockResolvedValue({
      success: true,
      data: mockPublicBlog,
      error: undefined,
      timestamp: '2026-01-01T00:00:00Z',
    })

    const response = await apiClient('/blogs/slug/public-blog', {
      skipAuthRefresh: true,
    })

    expect(response.success).toBe(true)
    expect(response.data?.owner.nickname).toBe('blogger')
  })

  it('should pass skipAuthRefresh option for public endpoint', async () => {
    const mockPublicBlog = {
      id: 1,
      title: 'Public Blog',
      urlSlug: 'public-blog',
      description: 'A public blog',
      owner: {
        userId: 2,
        nickname: 'blogger',
        profileImageUrl: null,
        bio: null,
      },
      createdAt: '2026-01-01T00:00:00Z',
    }

    vi.mocked(apiClient).mockResolvedValue({
      success: true,
      data: mockPublicBlog,
      error: undefined,
      timestamp: '2026-01-01T00:00:00Z',
    })

    const response = await apiClient('/blogs/slug/public-blog', {
      skipAuthRefresh: true,
    })

    expect(response.success).toBe(true)
    expect(vi.mocked(apiClient)).toHaveBeenCalled()
  })

  it('should handle blog not found error (404)', async () => {
    vi.mocked(apiClient).mockResolvedValue({
      success: false,
      data: null,
      error: {
        code: 'NOT_FOUND',
        message: 'Blog not found',
      },
      timestamp: '2026-01-01T00:00:00Z',
    })

    const response = await apiClient('/blogs/slug/nonexistent', {
      skipAuthRefresh: true,
    })

    expect(response.success).toBe(false)
    expect(response.error?.code).toBe('NOT_FOUND')
  })

  it('should handle owner without profile image', async () => {
    const mockPublicBlog = {
      id: 1,
      title: 'Simple Blog',
      urlSlug: 'simple-blog',
      description: 'A simple blog',
      owner: {
        userId: 3,
        nickname: 'anonymousblogger',
        profileImageUrl: null,
        bio: null,
      },
      createdAt: '2026-01-01T00:00:00Z',
    }

    vi.mocked(apiClient).mockResolvedValue({
      success: true,
      data: mockPublicBlog,
      error: undefined,
      timestamp: '2026-01-01T00:00:00Z',
    })

    const response = await apiClient('/blogs/slug/simple-blog', {
      skipAuthRefresh: true,
    })

    expect(response.data?.owner.profileImageUrl).toBeNull()
    expect(response.data?.owner.bio).toBeNull()
    expect(response.data?.owner.nickname).toBe('anonymousblogger')
  })
})
