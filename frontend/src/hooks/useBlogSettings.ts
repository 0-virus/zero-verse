import { useState, useCallback } from 'react'
import { apiClient } from '../lib/apiClient'
import type { BlogSettingsResponse, BlogSettingsRequest, BlogInitialSetupRequest } from '../types/settings'

export const useBlogSettings = () => {
  const [blog, setBlog] = useState<BlogSettingsResponse | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<{ code: string; message: string } | null>(null)

  const getBlogMe = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const response = await apiClient<BlogSettingsResponse>('/blogs/me', {
        method: 'GET',
      })
      if (response.success && response.data) {
        setBlog(response.data)
        return response.data
      } else {
        setError(response.error || { code: 'ERROR', message: 'Failed to fetch blog settings' })
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

  const updateBlog = useCallback(async (data: BlogSettingsRequest) => {
    setIsLoading(true)
    setError(null)
    try {
      const response = await apiClient<BlogSettingsResponse>('/blogs/me', {
        method: 'PUT',
        body: JSON.stringify(data),
      })
      if (response.success && response.data) {
        setBlog(response.data)
        return response.data
      } else {
        setError(response.error || { code: 'ERROR', message: 'Failed to update blog' })
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

  const initialSetup = useCallback(async (data: BlogInitialSetupRequest) => {
    setIsLoading(true)
    setError(null)
    try {
      const response = await apiClient<BlogSettingsResponse>('/blogs/me/initial-setup', {
        method: 'PUT',
        body: JSON.stringify(data),
      })
      if (response.success && response.data) {
        setBlog(response.data)
        return response.data
      } else {
        setError(response.error || { code: 'ERROR', message: 'Failed to complete setup' })
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

  return {
    blog,
    isLoading,
    error,
    getBlogMe,
    updateBlog,
    initialSetup,
  }
}
