import React, { createContext, useContext, useState, useCallback, useEffect } from 'react'
import { apiClient, setAccessToken as setApiAccessToken } from './apiClient'

export interface User {
  id: string
  email: string
  nickname: string
  name: string
  birthDate?: string
  profileImageUrl?: string
  role: 'USER' | 'ADMIN'
  status: 'ACTIVE' | 'SUSPENDED'
  createdAt: string
}

export interface AuthContextValue {
  user: User | null
  accessToken: string | null
  isLoading: boolean
  signin: (email: string, password: string) => Promise<void>
  signout: () => Promise<void>
  refreshAccessToken: () => Promise<boolean>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null)
  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  // Initialize auth on mount
  useEffect(() => {
    const initializeAuth = async () => {
      try {
        // Try to refresh token from cookie
        const refreshed = await refreshAccessToken()
        if (refreshed) {
          // Load user info
          await loadUserInfo()
        }
      } catch (error) {
        console.error('Failed to initialize auth:', error)
      } finally {
        setIsLoading(false)
      }
    }

    initializeAuth()
  }, [])

  const loadUserInfo = useCallback(async () => {
    try {
      const response = await apiClient('/auth/me', {
        method: 'GET',
      })
      if (response.success && response.data) {
        setUser(response.data)
      }
    } catch (error) {
      console.error('Failed to load user info:', error)
    }
  }, [])

  const signin = useCallback(async (email: string, password: string) => {
    try {
      const response = await apiClient('/auth/signin', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      if (response.success && response.data) {
        const token = response.data.accessToken
        // Update React state
        setAccessToken(token)
        // Sync with apiClient token store (CRITICAL for Bearer header)
        setApiAccessToken(token)
        await loadUserInfo()
      } else {
        throw new Error(response.error?.message || 'Signin failed')
      }
    } catch (error) {
      throw error
    }
  }, [loadUserInfo])

  const signout = useCallback(async () => {
    try {
      await apiClient('/auth/signout', {
        method: 'POST',
      })
    } catch (error) {
      console.error('Signout error:', error)
    } finally {
      setUser(null)
      setAccessToken(null)
      // Sync with apiClient token store
      setApiAccessToken(null)
    }
  }, [])

  const refreshAccessToken = useCallback(async (): Promise<boolean> => {
    try {
      const response = await apiClient('/auth/refresh', {
        method: 'POST',
      })
      if (response.success && response.data?.accessToken) {
        const token = response.data.accessToken
        // Update React state
        setAccessToken(token)
        // Sync with apiClient token store (CRITICAL for Bearer header)
        setApiAccessToken(token)
        return true
      }
      return false
    } catch (error) {
      console.error('Token refresh failed:', error)
      setUser(null)
      setAccessToken(null)
      // Sync with apiClient token store
      setApiAccessToken(null)
      return false
    }
  }, [])

  const value: AuthContextValue = {
    user,
    accessToken,
    isLoading,
    signin,
    signout,
    refreshAccessToken,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}
