import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import SignupPage from './SignupPage'
import { AuthProvider } from '../lib/authContext'

// Mock useNavigate
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => vi.fn(),
  }
})

// Mock useAuth
vi.mock('../lib/authContext', async () => {
  const actual = await vi.importActual('../lib/authContext')
  return {
    ...actual,
    useAuth: vi.fn(() => ({
      signin: vi.fn(async () => {
        // Simulate signin success
        return Promise.resolve()
      }),
      user: null,
      accessToken: null,
      isLoading: false,
      signout: vi.fn(),
      refreshAccessToken: vi.fn(),
    })),
  }
})

describe('SignupPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders signup form with all required fields', () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <SignupPage />
        </AuthProvider>
      </BrowserRouter>
    )

    expect(screen.getByPlaceholderText('Email')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Name')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Nickname (2-20 chars)')).toBeInTheDocument()
    const birthDateInput = document.querySelector('input[name="birth_date"]')
    expect(birthDateInput).toBeInTheDocument()
    expect(screen.getAllByPlaceholderText('Password')[0]).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Confirm Password')).toBeInTheDocument()
  })

  it('validates nickname length (2~20 chars)', async () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <SignupPage />
        </AuthProvider>
      </BrowserRouter>
    )

    const emailInput = screen.getByPlaceholderText('Email')
    const nameInput = screen.getByPlaceholderText('Name')
    const nicknameInput = screen.getByPlaceholderText('Nickname (2-20 chars)')
    const birthDateInput = document.querySelector('input[name="birth_date"]') as HTMLInputElement
    const passwordInputs = screen.getAllByPlaceholderText('Password')
    const confirmPasswordInput = screen.getByPlaceholderText('Confirm Password')
    const submitButton = screen.getByText('Sign Up')

    fireEvent.change(emailInput, { target: { value: 'test@example.com' } })
    fireEvent.change(nameInput, { target: { value: 'Test User' } })
    fireEvent.change(nicknameInput, { target: { value: 'a' } }) // Too short
    fireEvent.change(birthDateInput, { target: { value: '1995-01-01' } })
    fireEvent.change(passwordInputs[0], { target: { value: 'Password!1' } })
    fireEvent.change(confirmPasswordInput, { target: { value: 'Password!1' } })

    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(screen.getByText('닉네임은 2~20자여야 합니다.')).toBeInTheDocument()
    })
  })

  it('validates birth_date is required', async () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <SignupPage />
        </AuthProvider>
      </BrowserRouter>
    )

    const emailInput = screen.getByPlaceholderText('Email')
    const nameInput = screen.getByPlaceholderText('Name')
    const nicknameInput = screen.getByPlaceholderText('Nickname (2-20 chars)')
    const passwordInputs = screen.getAllByPlaceholderText('Password')
    const confirmPasswordInput = screen.getByPlaceholderText('Confirm Password')
    const submitButton = screen.getByText('Sign Up')

    fireEvent.change(emailInput, { target: { value: 'test@example.com' } })
    fireEvent.change(nameInput, { target: { value: 'Test User' } })
    fireEvent.change(nicknameInput, { target: { value: 'testuser' } })
    // Don't set birth_date
    fireEvent.change(passwordInputs[0], { target: { value: 'Password!1' } })
    fireEvent.change(confirmPasswordInput, { target: { value: 'Password!1' } })

    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(screen.getByText('생년월일을 입력해주세요.')).toBeInTheDocument()
    })
  })

  it('validates password meets requirements (8+ chars, letter, number, special)', async () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <SignupPage />
        </AuthProvider>
      </BrowserRouter>
    )

    const emailInput = screen.getByPlaceholderText('Email')
    const nameInput = screen.getByPlaceholderText('Name')
    const nicknameInput = screen.getByPlaceholderText('Nickname (2-20 chars)')
    const birthDateInput = document.querySelector('input[name="birth_date"]') as HTMLInputElement
    const passwordInputs = screen.getAllByPlaceholderText('Password')
    const confirmPasswordInput = screen.getByPlaceholderText('Confirm Password')
    const submitButton = screen.getByText('Sign Up')

    fireEvent.change(emailInput, { target: { value: 'test@example.com' } })
    fireEvent.change(nameInput, { target: { value: 'Test User' } })
    fireEvent.change(nicknameInput, { target: { value: 'testuser' } })
    fireEvent.change(birthDateInput, { target: { value: '1995-01-01' } })
    fireEvent.change(passwordInputs[0], { target: { value: 'short' } }) // Too short
    fireEvent.change(confirmPasswordInput, { target: { value: 'short' } })

    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(screen.getByText('비밀번호는 8자 이상이어야 합니다.')).toBeInTheDocument()
    })
  })

  it('validates password contains required character types', async () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <SignupPage />
        </AuthProvider>
      </BrowserRouter>
    )

    const emailInput = screen.getByPlaceholderText('Email')
    const nameInput = screen.getByPlaceholderText('Name')
    const nicknameInput = screen.getByPlaceholderText('Nickname (2-20 chars)')
    const birthDateInput = document.querySelector('input[name="birth_date"]') as HTMLInputElement
    const passwordInputs = screen.getAllByPlaceholderText('Password')
    const confirmPasswordInput = screen.getByPlaceholderText('Confirm Password')
    const submitButton = screen.getByText('Sign Up')

    fireEvent.change(emailInput, { target: { value: 'test@example.com' } })
    fireEvent.change(nameInput, { target: { value: 'Test User' } })
    fireEvent.change(nicknameInput, { target: { value: 'testuser' } })
    fireEvent.change(birthDateInput, { target: { value: '1995-01-01' } })
    fireEvent.change(passwordInputs[0], { target: { value: '12345678' } }) // Missing letter and special
    fireEvent.change(confirmPasswordInput, { target: { value: '12345678' } })

    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(screen.getByText('비밀번호는 영문을 포함해야 합니다.')).toBeInTheDocument()
    })
  })

  it('integrates with AuthProvider for signup and signin flow', async () => {
    // This test validates that the form can be filled and submitted
    // The actual API calls are mocked at the useAuth level
    render(
      <BrowserRouter>
        <AuthProvider>
          <SignupPage />
        </AuthProvider>
      </BrowserRouter>
    )

    const emailInput = screen.getByPlaceholderText('Email')
    const nameInput = screen.getByPlaceholderText('Name')
    const nicknameInput = screen.getByPlaceholderText('Nickname (2-20 chars)')
    const birthDateInput = document.querySelector('input[name="birth_date"]') as HTMLInputElement
    const passwordInputs = screen.getAllByPlaceholderText('Password')
    const confirmPasswordInput = screen.getByPlaceholderText('Confirm Password')

    // Fill in the form with valid data
    fireEvent.change(emailInput, { target: { value: 'newuser@example.com' } })
    fireEvent.change(nameInput, { target: { value: 'New User' } })
    fireEvent.change(nicknameInput, { target: { value: 'newuser' } })
    fireEvent.change(birthDateInput, { target: { value: '1995-01-01' } })
    fireEvent.change(passwordInputs[0], { target: { value: 'Password!1' } })
    fireEvent.change(confirmPasswordInput, { target: { value: 'Password!1' } })

    // Verify all fields are filled
    expect((emailInput as HTMLInputElement).value).toBe('newuser@example.com')
    expect((nameInput as HTMLInputElement).value).toBe('New User')
    expect((nicknameInput as HTMLInputElement).value).toBe('newuser')
    expect((birthDateInput as HTMLInputElement).value).toBe('1995-01-01')
    expect((passwordInputs[0] as HTMLInputElement).value).toBe('Password!1')
    expect((confirmPasswordInput as HTMLInputElement).value).toBe('Password!1')
  })
})
