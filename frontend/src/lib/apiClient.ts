const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: {
    code: string
    message: string
  }
  timestamp: string
}

let refreshPromise: Promise<boolean> | null = null
let accessToken: string | null = null
let onUnauthorized: (() => void) | null = null

export const setAccessToken = (token: string | null) => {
  accessToken = token
}

export const getAccessToken = () => accessToken

export const setOnUnauthorized = (callback: (() => void) | null) => {
  onUnauthorized = callback
}

const performRefresh = async (): Promise<boolean> => {
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
      },
    })

    if (response.ok) {
      const data = await response.json()
      if (data.success && data.data?.accessToken) {
        setAccessToken(data.data.accessToken)
        return true
      }
    }
    return false
  } catch (error) {
    console.error('Token refresh failed:', error)
    return false
  }
}

export const apiClient = async <T = any>(
  endpoint: string,
  options: RequestInit = {}
): Promise<ApiResponse<T>> => {
  const headers = new Headers(options.headers || {})
  headers.set('Content-Type', 'application/json')

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  let response = await fetch(`${API_BASE_URL}/api/v1${endpoint}`, {
    ...options,
    headers,
    credentials: 'include',
  })

  // Handle 401 - try to refresh and retry once (but never for refresh endpoint itself)
  if (response && response.status === 401 && endpoint !== '/auth/refresh') {
    if (!refreshPromise) {
      refreshPromise = performRefresh()
    }

    const refreshed = await refreshPromise
    refreshPromise = null

    if (refreshed && accessToken) {
      const retryHeaders = new Headers(headers)
      retryHeaders.set('Authorization', `Bearer ${accessToken}`)

      response = await fetch(`${API_BASE_URL}/api/v1${endpoint}`, {
        ...options,
        headers: retryHeaders,
        credentials: 'include',
      })
    } else {
      // Refresh failed, need to re-authenticate
      setAccessToken(null)
      if (onUnauthorized) {
        onUnauthorized()
      } else {
        window.location.href = '/signin'
      }
      return {
        success: false,
        error: {
          code: 'AUTH_REQUIRED',
          message: 'Please sign in again',
        },
        timestamp: new Date().toISOString(),
      }
    }
  }

  try {
    const data: ApiResponse<T> = await response.json()
    return data
  } catch (error) {
    return {
      success: false,
      error: {
        code: 'PARSE_ERROR',
        message: 'Failed to parse response',
      },
      timestamp: new Date().toISOString(),
    }
  }
}
