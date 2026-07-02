import { useState, useCallback } from 'react'
import { apiClient } from '../lib/apiClient'
import type { UserSettingsResponse, UserSettingsRequest, ChangePasswordRequest } from '../types/settings'

export const useUserSettings = () => {
  const [user, setUser] = useState<UserSettingsResponse | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<{ code: string; message: string } | null>(null)

  const getMe = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const response = await apiClient<UserSettingsResponse>('/users/me', {
        method: 'GET',
      })
      if (response.success && response.data) {
        setUser(response.data)
        return response.data
      } else {
        setError(response.error || { code: 'ERROR', message: 'Failed to fetch user settings' })
        return null
      }
    } catch (err: any) {
      const errorObj = { code: 'ERROR', message: err.message || 'An error occurred' }
      setError(errorObj)
      return null
    } finally {
      setIsLoading(false)
    }
  }, [])

  const updateProfile = useCallback(async (data: UserSettingsRequest) => {
    setIsLoading(true)
    setError(null)
    try {
      const response = await apiClient<UserSettingsResponse>('/users/me', {
        method: 'PUT',
        body: JSON.stringify(data),
      })
      if (response.success && response.data) {
        setUser(response.data)
        return response.data
      } else {
        setError(response.error || { code: 'ERROR', message: 'Failed to update profile' })
        return null
      }
    } catch (err: any) {
      const errorObj = { code: 'ERROR', message: err.message || 'An error occurred' }
      setError(errorObj)
      return null
    } finally {
      setIsLoading(false)
    }
  }, [])

  const changePassword = useCallback(async (data: ChangePasswordRequest) => {
    setIsLoading(true)
    setError(null)
    try {
      const response = await apiClient<{ message: string }>('/users/me/password', {
        method: 'PUT',
        body: JSON.stringify(data),
      })
      if (response.success) {
        return true
      } else {
        setError(response.error || { code: 'ERROR', message: 'Failed to change password' })
        return false
      }
    } catch (err: any) {
      const errorObj = { code: 'ERROR', message: err.message || 'An error occurred' }
      setError(errorObj)
      return false
    } finally {
      setIsLoading(false)
    }
  }, [])

  return {
    user,
    isLoading,
    error,
    getMe,
    updateProfile,
    changePassword,
  }
}
