import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { AuthProvider, useAuth } from './authContext'
import * as apiClientModule from './apiClient'

// Mock apiClient
vi.mock('./apiClient', () => ({
  apiClient: vi.fn(),
  setAccessToken: vi.fn(),
  getAccessToken: vi.fn(),
  setOnUnauthorized: vi.fn(),
}))

const mockApiClient = apiClientModule.apiClient as any
const mockSetAccessToken = apiClientModule.setAccessToken as any
const mockSetOnUnauthorized = apiClientModule.setOnUnauthorized as any

// Test component to use the auth context
function TestComponent() {
  const { user, accessToken, isLoading, signin, signout } = useAuth()

  if (isLoading) {
    return <div>Loading...</div>
  }

  return (
    <div>
      {user ? (
        <>
          <div data-testid="user-email">{user.email}</div>
          <div data-testid="user-nickname">{user.nickname}</div>
          <div data-testid="setup-status">{user.defaultBlog?.isSetupCompleted ? 'completed' : 'pending'}</div>
          <div data-testid="access-token">{accessToken || 'no-token'}</div>
          <button onClick={() => signout()}>Sign Out</button>
        </>
      ) : (
        <>
          <div data-testid="not-authenticated">Not authenticated</div>
          <button
            onClick={() => signin('test@example.com', 'Password!1')}
            data-testid="signin-button"
          >
            Sign In
          </button>
        </>
      )}
    </div>
  )
}

describe('AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('initializes with refresh -> me on mount', async () => {
    mockApiClient
      .mockResolvedValueOnce({
        success: true,
        data: {
          accessToken: 'fresh-token',
          tokenType: 'Bearer',
          expiresIn: 3600,
        },
      })
      .mockResolvedValueOnce({
        success: true,
        data: {
          id: 1,
          email: 'test@example.com',
          nickname: 'testuser',
          name: 'Test User',
          birthDate: '1995-01-01',
          role: 'USER',
          status: 'ACTIVE',
          profileImageUrl: null,
          defaultBlog: {
            id: 1,
            title: "testuser's Blog",
            urlSlug: 'testuser',
            isSetupCompleted: false,
          },
        },
      })

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.queryByText('Loading...')).not.toBeInTheDocument()
    })

    expect(screen.getByTestId('user-email')).toHaveTextContent('test@example.com')
    expect(screen.getByTestId('setup-status')).toHaveTextContent('pending')
  })

  it('handles signin with user and defaultBlog', async () => {
    mockApiClient.mockResolvedValue({
      success: true,
      data: {
        id: 1,
        email: 'test@example.com',
        nickname: 'testuser',
        name: 'Test User',
        birthDate: '1995-01-01',
        role: 'USER',
        status: 'ACTIVE',
        profileImageUrl: null,
        defaultBlog: {
          id: 1,
          title: "testuser's Blog",
          urlSlug: 'testuser',
          isSetupCompleted: false,
        },
      },
    })

    // Set up refresh to fail (no cookie)
    mockApiClient.mockResolvedValueOnce({
      success: false,
      error: { code: 'UNAUTHORIZED', message: 'No refresh token' },
    })

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.queryByText('Loading...')).not.toBeInTheDocument()
    })

    const notAuthElement = screen.getByTestId('not-authenticated')
    expect(notAuthElement).toBeInTheDocument()
  })

  it('clears auth state on signout', async () => {
    mockApiClient
      .mockResolvedValueOnce({
        success: true,
        data: {
          accessToken: 'token',
          tokenType: 'Bearer',
          expiresIn: 3600,
        },
      })
      .mockResolvedValueOnce({
        success: true,
        data: {
          id: 1,
          email: 'test@example.com',
          nickname: 'testuser',
          name: 'Test User',
          birthDate: '1995-01-01',
          role: 'USER',
          status: 'ACTIVE',
          profileImageUrl: null,
          defaultBlog: {
            id: 1,
            title: "testuser's Blog",
            urlSlug: 'testuser',
            isSetupCompleted: false,
          },
        },
      })
      .mockResolvedValueOnce({ success: true, data: {} })

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.getByTestId('user-email')).toBeInTheDocument()
    })

    const signoutButton = screen.getByText('Sign Out')
    signoutButton.click()

    await waitFor(() => {
      expect(screen.getByTestId('not-authenticated')).toBeInTheDocument()
    })

    expect(mockSetAccessToken).toHaveBeenCalledWith(null)
  })

  it('registers onUnauthorized callback on mount', async () => {
    mockApiClient.mockResolvedValue({
      success: false,
      error: { code: 'UNAUTHORIZED', message: 'No token' },
    })

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(mockSetOnUnauthorized).toHaveBeenCalled()
    })
  })

  it('handles refresh failure during initialization', async () => {
    mockApiClient.mockResolvedValue({
      success: false,
      error: { code: 'UNAUTHORIZED', message: 'Refresh failed' },
    })

    render(
      <AuthProvider>
        <TestComponent />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.queryByText('Loading...')).not.toBeInTheDocument()
    })

    expect(screen.getByTestId('not-authenticated')).toBeInTheDocument()
  })
})
