import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import MainPage from './MainPage'
import { AuthProvider } from '../lib/authContext'

// Mock apiClient to avoid actual API calls
vi.mock('../lib/apiClient', () => ({
  apiClient: vi.fn(),
  setAccessToken: vi.fn(),
  getAccessToken: vi.fn(() => null),
}))

describe('MainPage', () => {
  it('should render main page with heading', () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <MainPage />
        </AuthProvider>
      </BrowserRouter>
    )

    const heading = screen.getByText(/유니버스 새 소식/i)
    expect(heading).toBeDefined()
  })

  it('should render with tab buttons', () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <MainPage />
        </AuthProvider>
      </BrowserRouter>
    )

    const buttons = screen.getAllByRole('button')
    expect(buttons.length).toBeGreaterThan(0)
  })
})
