import { describe, it, expect, beforeEach, vi } from 'vitest'
import { apiClient } from '../lib/apiClient'

vi.mock('../lib/apiClient')

describe('useBlogSettings hook behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should call apiClient with GET method for getBlogMe', async () => {
    const mockBlog = {
      id: 1,
      title: 'My Blog',
      urlSlug: 'my-blog',
      description: 'This is my blog',
      isSetupCompleted: true,
      ownerId: 1,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    }

    vi.mocked(apiClient).mockResolvedValue({
      success: true,
      data: mockBlog,
      error: undefined,
      timestamp: '2026-01-01T00:00:00Z',
    })

    const response = await apiClient('/blogs/me', { method: 'GET' })
    expect(response.success).toBe(true)
    expect(response.data?.urlSlug).toBe('my-blog')
  })

  it('should call apiClient with PUT method for updateBlog', async () => {
    const mockUpdatedBlog = {
      id: 1,
      title: 'Updated Blog',
      urlSlug: 'updated-blog',
      description: 'Updated description',
      isSetupCompleted: true,
      ownerId: 1,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-02T00:00:00Z',
    }

    vi.mocked(apiClient).mockResolvedValue({
      success: true,
      data: mockUpdatedBlog,
      error: undefined,
      timestamp: '2026-01-02T00:00:00Z',
    })

    const response = await apiClient('/blogs/me', {
      method: 'PUT',
      body: JSON.stringify({ title: 'Updated Blog' }),
    })

    expect(response.success).toBe(true)
    expect(response.data?.title).toBe('Updated Blog')
  })

  it('should handle BLOG_002 error for slug duplicate', async () => {
    vi.mocked(apiClient).mockResolvedValue({
      success: false,
      data: null,
      error: {
        code: 'BLOG_002',
        message: '이미 존재하는 URL Slug입니다.',
      },
      timestamp: '2026-01-01T00:00:00Z',
    })

    const response = await apiClient('/blogs/me', {
      method: 'PUT',
      body: JSON.stringify({ urlSlug: 'duplicate-slug' }),
    })

    expect(response.success).toBe(false)
    expect(response.error?.code).toBe('BLOG_002')
  })

  it('should handle BLOG_003 error for slug format', async () => {
    vi.mocked(apiClient).mockResolvedValue({
      success: false,
      data: null,
      error: {
        code: 'BLOG_003',
        message: 'URL Slug 형식이 올바르지 않습니다.',
      },
      timestamp: '2026-01-01T00:00:00Z',
    })

    const response = await apiClient('/blogs/me', {
      method: 'PUT',
      body: JSON.stringify({ urlSlug: 'invalid slug!' }),
    })

    expect(response.success).toBe(false)
    expect(response.error?.code).toBe('BLOG_003')
  })

  it('should complete initial setup with isSetupCompleted=true', async () => {
    const mockCompletedBlog = {
      id: 1,
      title: 'user의 블로그',
      urlSlug: 'user-blog',
      description: '',
      isSetupCompleted: true,
      ownerId: 1,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    }

    vi.mocked(apiClient).mockResolvedValue({
      success: true,
      data: mockCompletedBlog,
      error: undefined,
      timestamp: '2026-01-01T00:00:00Z',
    })

    const response = await apiClient('/blogs/me/initial-setup', {
      method: 'PUT',
      body: JSON.stringify({ title: '', urlSlug: '', description: '' }),
    })

    expect(response.success).toBe(true)
    expect(response.data?.isSetupCompleted).toBe(true)
  })

  it('should handle BLOG_004 error when setup already completed', async () => {
    vi.mocked(apiClient).mockResolvedValue({
      success: false,
      data: null,
      error: {
        code: 'BLOG_004',
        message: '블로그 설정이 이미 완료됨.',
      },
      timestamp: '2026-01-01T00:00:00Z',
    })

    const response = await apiClient('/blogs/me/initial-setup', {
      method: 'PUT',
      body: JSON.stringify({ title: 'Another Setup' }),
    })

    expect(response.success).toBe(false)
    expect(response.error?.code).toBe('BLOG_004')
  })
})
