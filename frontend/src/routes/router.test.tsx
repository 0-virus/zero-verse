import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { RouterProvider } from 'react-router-dom'
import { render, waitFor } from '@testing-library/react'
import router from './router'
import { AuthProvider } from '../lib/authContext'
import * as apiClientModule from '../lib/apiClient'

// Mock apiClient
vi.mock('../lib/apiClient', () => ({
  apiClient: vi.fn(),
  setAccessToken: vi.fn(),
  getAccessToken: vi.fn(() => null),
  setOnUnauthorized: vi.fn(),
}))

const mockApiClient = apiClientModule.apiClient as any

function renderWithAuth() {
  return render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  )
}

describe('Router', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('should render main page on root route', () => {
    mockApiClient.mockResolvedValue({
      success: false,
      error: { code: 'UNAUTHORIZED', message: 'No token' },
    })

    router.navigate('/')
    const { container } = renderWithAuth()
    expect(container).toBeDefined()
  })

  it('should render signin page on /signin route', () => {
    mockApiClient.mockResolvedValue({
      success: false,
      error: { code: 'UNAUTHORIZED', message: 'No token' },
    })

    router.navigate('/signin')
    const { container } = renderWithAuth()
    expect(container).toBeDefined()
  })

  it('should render signup page on /signup route', () => {
    mockApiClient.mockResolvedValue({
      success: false,
      error: { code: 'UNAUTHORIZED', message: 'No token' },
    })

    router.navigate('/signup')
    const { container } = renderWithAuth()
    expect(container).toBeDefined()
  })

  it('should render blog setup page on /blog/setup route for unauthenticated users', () => {
    mockApiClient.mockResolvedValue({
      success: false,
      error: { code: 'UNAUTHORIZED', message: 'No token' },
    })

    router.navigate('/blog/setup')
    const { container } = renderWithAuth()
    expect(container).toBeDefined()
  })

  it('protects routes that require authentication', () => {
    mockApiClient.mockResolvedValue({
      success: false,
      error: { code: 'UNAUTHORIZED', message: 'No token' },
    })

    router.navigate('/write')
    renderWithAuth()

    // Should navigate to /signin when accessing protected route without auth
    waitFor(() => {
      expect(router.state.location.pathname).toBe('/signin')
    })
  })

  it('blocks authenticated users from accessing signin/signup pages', async () => {
    // User is authenticated and setup is not completed
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

    router.navigate('/signin')
    renderWithAuth()

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/blog/setup')
    })
  })

  it('redirects setup complete users from setup page to home', async () => {
    // User is authenticated and setup is completed
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
            isSetupCompleted: true,
          },
        },
      })

    router.navigate('/blog/setup')
    renderWithAuth()

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/')
    })
  })
})
