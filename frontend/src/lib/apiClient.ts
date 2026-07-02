const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

// Public auth endpoints that should not trigger automatic refresh on 401
const PUBLIC_AUTH_ENDPOINTS = ['/auth/signin', '/auth/register', '/auth/signout']

export interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: {
    code: string
    message: string
  }
  timestamp: string
}

export interface ApiClientOptions extends RequestInit {
  skipAuthRefresh?: boolean
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
    return false
  }
}

export const apiClient = async <T = any>(
  endpoint: string,
  options: ApiClientOptions = {}
): Promise<ApiResponse<T>> => {
  const { skipAuthRefresh: forceSkipRefresh = false, ...fetchOptions } = options

  const headers = new Headers(fetchOptions.headers || {})
  headers.set('Content-Type', 'application/json')

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  let response = await fetch(`${API_BASE_URL}/api/v1${endpoint}`, {
    ...fetchOptions,
    headers,
    credentials: 'include',
  })

  // Handle 401 - try to refresh and retry once
  // Skip refresh for: /auth/refresh itself, public auth endpoints, or explicit skipAuthRefresh option
  const shouldSkipRefresh = forceSkipRefresh ||
    endpoint === '/auth/refresh' ||
    PUBLIC_AUTH_ENDPOINTS.includes(endpoint)

  if (response && response.status === 401 && !shouldSkipRefresh) {
    if (!refreshPromise) {
      refreshPromise = performRefresh()
    }

    const refreshed = await refreshPromise
    refreshPromise = null

    if (refreshed && accessToken) {
      const retryHeaders = new Headers(headers)
      retryHeaders.set('Authorization', `Bearer ${accessToken}`)

      response = await fetch(`${API_BASE_URL}/api/v1${endpoint}`, {
        ...fetchOptions,
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
