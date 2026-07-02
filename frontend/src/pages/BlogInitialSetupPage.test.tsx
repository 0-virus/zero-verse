import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import BlogInitialSetupPage from './BlogInitialSetupPage'

const initialSetupMock = vi.fn()
const refreshUserMock = vi.fn()
const navigateMock = vi.fn()

vi.mock('../hooks/useBlogSettings', () => ({
  useBlogSettings: () => ({
    blog: null,
    isLoading: false,
    error: null,
    getBlogMe: vi.fn(),
    updateBlog: vi.fn(),
    initialSetup: initialSetupMock,
  }),
}))

vi.mock('../lib/authContext', () => ({
  useAuth: () => ({
    refreshUser: refreshUserMock,
  }),
}))

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => navigateMock,
  }
})

describe('BlogInitialSetupPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should render blog setup form', () => {
    render(
      <BrowserRouter>
        <BlogInitialSetupPage />
      </BrowserRouter>
    )

    const heading = screen.getByText(/BLOG SETUP/i)
    expect(heading).toBeDefined()

    const submitButton = screen.getByRole('button', { name: /초기 설정 완료/i })
    expect(submitButton).toBeDefined()
  })

  it('should render form fields for slug, title, and description', () => {
    render(
      <BrowserRouter>
        <BlogInitialSetupPage />
      </BrowserRouter>
    )

    const slugInput = screen.getByPlaceholderText(/e.g., my-blog/i)
    const titleInput = screen.getByPlaceholderText(/기본값.*닉네임/i)
    const descriptionInput = screen.getByPlaceholderText(/블로그 설명/i)

    expect(slugInput).toBeDefined()
    expect(titleInput).toBeDefined()
    expect(descriptionInput).toBeDefined()
  })

  it('should accept form input and maintain state', () => {
    render(
      <BrowserRouter>
        <BlogInitialSetupPage />
      </BrowserRouter>
    )

    const slugInput = screen.getByPlaceholderText(/e.g., my-blog/i) as HTMLInputElement
    const titleInput = screen.getByPlaceholderText(/기본값.*닉네임/i) as HTMLInputElement

    fireEvent.change(slugInput, { target: { value: 'my-awesome-blog' } })
    fireEvent.change(titleInput, { target: { value: 'My Awesome Blog' } })

    expect(slugInput.value).toBe('my-awesome-blog')
    expect(titleInput.value).toBe('My Awesome Blog')
  })

  it('should have helper text about optional fields', () => {
    render(
      <BrowserRouter>
        <BlogInitialSetupPage />
      </BrowserRouter>
    )

    const helperText = screen.getByText(/빈 칸은 서버에서 자동으로 채워집니다/i)
    expect(helperText).toBeDefined()
  })

  it('should label form fields appropriately', () => {
    render(
      <BrowserRouter>
        <BlogInitialSetupPage />
      </BrowserRouter>
    )

    const slugLabel = screen.getByText(/URL Slug.*선택/i)
    const titleLabel = screen.getByText(/블로그 제목.*선택/i)
    const descriptionLabel = screen.getByText(/설명.*선택/i)

    expect(slugLabel).toBeDefined()
    expect(titleLabel).toBeDefined()
    expect(descriptionLabel).toBeDefined()
  })

  it('should submit form with data', () => {
    render(
      <BrowserRouter>
        <BlogInitialSetupPage />
      </BrowserRouter>
    )

    const form = screen.getByRole('button', { name: /초기 설정 완료/i }).closest('form')
    expect(form).toBeDefined()
  })

  it('on successful setup, refreshes authContext then navigates to the blog page', async () => {
    initialSetupMock.mockResolvedValueOnce({ urlSlug: 'my-awesome-blog' })
    refreshUserMock.mockResolvedValueOnce(undefined)

    render(
      <BrowserRouter>
        <BlogInitialSetupPage />
      </BrowserRouter>
    )

    fireEvent.change(screen.getByPlaceholderText(/e.g., my-blog/i), {
      target: { value: 'my-awesome-blog' },
    })
    fireEvent.click(screen.getByRole('button', { name: /초기 설정 완료/i }))

    await waitFor(() => {
      expect(initialSetupMock).toHaveBeenCalledTimes(1)
      // #4 regression guard: authContext must be refreshed so defaultBlog is not stale
      expect(refreshUserMock).toHaveBeenCalledTimes(1)
      expect(navigateMock).toHaveBeenCalledWith('/blog/my-awesome-blog')
    })
  })

  it('shows an error and does not navigate when setup fails', async () => {
    initialSetupMock.mockResolvedValueOnce(null)

    render(
      <BrowserRouter>
        <BlogInitialSetupPage />
      </BrowserRouter>
    )

    fireEvent.click(screen.getByRole('button', { name: /초기 설정 완료/i }))

    await waitFor(() => {
      expect(screen.getByText(/블로그 초기 설정에 실패했습니다/i)).toBeDefined()
    })
    expect(refreshUserMock).not.toHaveBeenCalled()
    expect(navigateMock).not.toHaveBeenCalled()
  })
})
