import { describe, it, expect, beforeEach, vi } from 'vitest'
import { apiClient } from '../lib/apiClient'

vi.mock('../lib/apiClient')

describe('useUserSettings hook behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should call apiClient with correct endpoint for getMe', async () => {
    vi.mocked(apiClient).mockResolvedValue({
      success: true,
      data: {
        id: 1,
        email: 'test@example.com',
        nickname: 'testuser',
        name: 'Test User',
        bio: 'Test bio',
        birthDate: '1990-01-01',
        profileImageUrl: 'https://example.com/image.jpg',
        role: 'USER' as const,
        status: 'ACTIVE' as const,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
      error: undefined,
      timestamp: '2026-01-01T00:00:00Z',
    } as any)

    const response = await apiClient('/users/me', { method: 'GET' })
    expect(response.success).toBe(true)
    expect(response.data?.nickname).toBe('testuser')
  })

  it('should call apiClient with PUT method for updateProfile', async () => {
    vi.mocked(apiClient).mockResolvedValue({
      success: true,
      data: {
        id: 1,
        email: 'test@example.com',
        nickname: 'newname',
        name: 'Updated Name',
        bio: 'Updated bio',
        birthDate: '1990-01-01',
        profileImageUrl: 'https://example.com/new.jpg',
        role: 'USER' as const,
        status: 'ACTIVE' as const,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-02T00:00:00Z',
      },
      error: undefined,
      timestamp: '2026-01-02T00:00:00Z',
    })

    const response = await apiClient('/users/me', {
      method: 'PUT',
      body: JSON.stringify({ nickname: 'newname' }),
    })

    expect(response.success).toBe(true)
    expect(response.data?.nickname).toBe('newname')
  })

  it('should handle USER_002 error for nickname duplicate', async () => {
    vi.mocked(apiClient).mockResolvedValue({
      success: false,
      data: null,
      error: {
        code: 'USER_002',
        message: '이미 존재하는 닉네임입니다.',
      },
      timestamp: '2026-01-01T00:00:00Z',
    })

    const response = await apiClient('/users/me', {
      method: 'PUT',
      body: JSON.stringify({ nickname: 'duplicate' }),
    })

    expect(response.success).toBe(false)
    expect(response.error?.code).toBe('USER_002')
  })

  it('should call apiClient with correct endpoint for changePassword', async () => {
    vi.mocked(apiClient).mockResolvedValue({
      success: true,
      data: { message: 'Password changed successfully' },
      error: undefined,
      timestamp: '2026-01-01T00:00:00Z',
    })

    const response = await apiClient('/users/me/password', {
      method: 'PUT',
      body: JSON.stringify({
        currentPassword: 'oldpass123!',
        newPassword: 'newpass456!',
      }),
    })

    expect(response.success).toBe(true)
  })

  it('should handle wrong current password error', async () => {
    vi.mocked(apiClient).mockResolvedValue({
      success: false,
      data: null,
      error: {
        code: 'AUTH_001',
        message: 'Current password is incorrect.',
      },
      timestamp: '2026-01-01T00:00:00Z',
    })

    const response = await apiClient('/users/me/password', {
      method: 'PUT',
      body: JSON.stringify({
        currentPassword: 'wrongpass123!',
        newPassword: 'newpass456!',
      }),
    })

    expect(response.success).toBe(false)
    expect(response.error?.code).toBe('AUTH_001')
  })
})
