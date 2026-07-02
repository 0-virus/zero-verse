import { useState, useCallback } from 'react'
import { apiClient } from '../lib/apiClient'
import type { BlogPublicResponse } from '../types/settings'

export const useBlogPublic = () => {
  const [blog, setBlog] = useState<BlogPublicResponse | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<{ code: string; message: string } | null>(null)

  const getPublicBlog = useCallback(async (urlSlug: string) => {
    setIsLoading(true)
    setError(null)
    try {
      const response = await apiClient<BlogPublicResponse>(`/blogs/slug/${urlSlug}`, {
        method: 'GET',
        skipAuthRefresh: true, // Public endpoint, don't auto-refresh on 401
      })
      if (response.success && response.data) {
        setBlog(response.data)
        return response.data
      } else {
        setError(response.error || { code: 'NOT_FOUND', message: 'Blog not found' })
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
    getPublicBlog,
  }
}
