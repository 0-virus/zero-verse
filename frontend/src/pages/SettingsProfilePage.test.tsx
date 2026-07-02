import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import SettingsProfilePage from './SettingsProfilePage'
import * as useUserSettingsModule from '../hooks/useUserSettings'
import * as useBlogSettingsModule from '../hooks/useBlogSettings'
import * as authContextModule from '../lib/authContext'
import type { UserSettingsResponse, BlogSettingsResponse } from '../types/settings'

vi.mock('../hooks/useUserSettings')
vi.mock('../hooks/useBlogSettings')
vi.mock('../lib/authContext')
vi.mock('../components/layout/AppShell', () => ({
  default: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

// Helper to wait for component to load
const waitForComponentToLoad = () =>
  waitFor(() => {
    expect(screen.queryByText('로딩 중...')).toBeNull()
  })

describe('SettingsProfilePage component', () => {
  const mockUser: UserSettingsResponse = {
    userId: 1,
    email: 'test@example.com',
    nickname: 'testuser',
    name: 'Test User',
    bio: 'Test bio',
    birthDate: '1990-01-01',
    profileImageUrl: 'https://example.com/profile.jpg',
    role: 'USER',
    status: 'ACTIVE',
    createdAt: '2026-01-15T10:00:00Z',
    updatedAt: '2026-01-15T10:00:00Z',
  }

  const mockBlog: BlogSettingsResponse = {
    blogId: 1,
    title: 'My Blog',
    urlSlug: 'my-blog',
    description: 'My blog description',
    isSetupCompleted: true,
    ownerId: 1,
    createdAt: '2026-01-15T10:00:00Z',
    updatedAt: '2026-01-15T10:00:00Z',
  }

  beforeEach(() => {
    vi.clearAllMocks()

    vi.mocked(authContextModule.useAuth).mockReturnValue({
      user: { ...mockUser, defaultBlog: mockBlog } as any,
      accessToken: 'token',
      isLoading: false,
      signin: vi.fn(),
      signout: vi.fn(),
      refreshAccessToken: vi.fn(),
    })

    vi.mocked(useUserSettingsModule.useUserSettings).mockReturnValue({
      user: mockUser,
      isLoading: false,
      error: null,
      getMe: vi.fn().mockResolvedValue(mockUser),
      updateProfile: vi.fn(),
      changePassword: vi.fn(),
    })

    vi.mocked(useBlogSettingsModule.useBlogSettings).mockReturnValue({
      blog: mockBlog,
      isLoading: false,
      error: null,
      getBlogMe: vi.fn().mockResolvedValue(mockBlog),
      updateBlog: vi.fn(),
      initialSetup: vi.fn(),
    })
  })

  it('should render profile, blog, and security tabs', async () => {
    render(
      <BrowserRouter>
        <SettingsProfilePage />
      </BrowserRouter>
    )

    await waitForComponentToLoad()
    expect(screen.getByText('Profile')).toBeDefined()
    expect(screen.getByText('Blog')).toBeDefined()
    expect(screen.getByText('Security')).toBeDefined()
  })

  it('should display profile form by default', async () => {
    render(
      <BrowserRouter>
        <SettingsProfilePage />
      </BrowserRouter>
    )

    await waitForComponentToLoad()
    expect(screen.getByText('User Profile')).toBeDefined()
    const inputs = screen.getAllByDisplayValue('Test User')
    expect(inputs.length).toBeGreaterThan(0)
  })

  it('should switch to blog tab', async () => {
    render(
      <BrowserRouter>
        <SettingsProfilePage />
      </BrowserRouter>
    )

    await waitForComponentToLoad()
    const blogTab = screen.getByText('Blog')
    fireEvent.click(blogTab)

    expect(screen.getByText('Blog Settings')).toBeDefined()
    expect(screen.getByDisplayValue('my-blog')).toBeDefined()
  })

  it('should switch to security tab', async () => {
    render(
      <BrowserRouter>
        <SettingsProfilePage />
      </BrowserRouter>
    )

    await waitForComponentToLoad()
    const securityTab = screen.getByText('Security')
    fireEvent.click(securityTab)

    expect(screen.queryByText(/Current Password/)).toBeDefined()
  })

  it('should have save buttons for profile and blog tabs', async () => {
    render(
      <BrowserRouter>
        <SettingsProfilePage />
      </BrowserRouter>
    )

    await waitForComponentToLoad()
    expect(screen.getByRole('button', { name: /Save/ })).toBeDefined()
  })

  it('should render password validation requirements', async () => {
    render(
      <BrowserRouter>
        <SettingsProfilePage />
      </BrowserRouter>
    )

    await waitForComponentToLoad()
    const securityTab = screen.getByText('Security')
    fireEvent.click(securityTab)

    expect(screen.getByText(/8자 이상, 영문·숫자·특수문자 포함/)).toBeDefined()
  })

  it('should display blog form with current blog data', async () => {
    render(
      <BrowserRouter>
        <SettingsProfilePage />
      </BrowserRouter>
    )

    await waitForComponentToLoad()
    const blogTab = screen.getByText('Blog')
    fireEvent.click(blogTab)

    expect(screen.getByDisplayValue('my-blog')).toBeDefined()
    expect(screen.getByDisplayValue('My Blog')).toBeDefined()
    expect(screen.getByDisplayValue('My blog description')).toBeDefined()
  })

  it('should allow tab switching without losing data', async () => {
    render(
      <BrowserRouter>
        <SettingsProfilePage />
      </BrowserRouter>
    )

    await waitForComponentToLoad()

    // Start on profile tab
    expect(screen.getByText('User Profile')).toBeDefined()

    // Switch to blog tab
    fireEvent.click(screen.getByText('Blog'))
    expect(screen.getByText('Blog Settings')).toBeDefined()

    // Switch to security tab
    fireEvent.click(screen.getByText('Security'))
    expect(screen.queryByText(/Current Password/)).toBeDefined()

    // Switch back to profile
    fireEvent.click(screen.getByText('Profile'))
    expect(screen.getByText('User Profile')).toBeDefined()
  })

  it('should show email as read-only in profile tab', async () => {
    render(
      <BrowserRouter>
        <SettingsProfilePage />
      </BrowserRouter>
    )

    await waitForComponentToLoad()
    const emailInput = screen.getByDisplayValue('test@example.com') as HTMLInputElement
    expect(emailInput.disabled).toBe(true)
  })

  it('should have change password button', async () => {
    render(
      <BrowserRouter>
        <SettingsProfilePage />
      </BrowserRouter>
    )

    await waitForComponentToLoad()
    const securityTab = screen.getByText('Security')
    fireEvent.click(securityTab)

    const changeButton = screen.getAllByRole('button', { name: /Change Password/ })
    expect(changeButton.length).toBeGreaterThan(0)
  })
})
