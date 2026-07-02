import { useState, useCallback } from 'react'
import { apiClient } from '../lib/apiClient'
import type {
  CategoryTreeNode,
  CategoryResponse,
  CreateCategoryRequest,
  UpdateCategoryRequest,
  ReorderCategoriesRequest,
  DeleteCategoryResponse,
} from '../types/category'

export const useCategories = () => {
  const [categories, setCategories] = useState<CategoryTreeNode[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<{ code: string; message: string } | null>(null)

  const getCategories = useCallback(async (blogId: number, includeDrafts = false) => {
    setIsLoading(true)
    setError(null)
    try {
      const response = await apiClient<CategoryTreeNode[]>(
        `/blogs/${blogId}/categories?includeDrafts=${includeDrafts}`,
        {
          method: 'GET',
        }
      )
      if (response.success && response.data) {
        setCategories(response.data)
        return response.data
      } else {
        setError(response.error || { code: 'ERROR', message: 'Failed to fetch categories' })
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

  const createCategory = useCallback(
    async (blogId: number, request: CreateCategoryRequest) => {
      setIsLoading(true)
      setError(null)
      try {
        const response = await apiClient<CategoryResponse>(`/blogs/${blogId}/categories`, {
          method: 'POST',
          body: JSON.stringify(request),
        })
        if (response.success && response.data) {
          return response.data
        } else {
          setError(response.error || { code: 'ERROR', message: 'Failed to create category' })
          return null
        }
      } catch (err: any) {
        const errorObj = { code: 'ERROR', message: err.message || 'An error occurred' }
        setError(errorObj)
        return null
      } finally {
        setIsLoading(false)
      }
    },
    []
  )

  const updateCategory = useCallback(
    async (blogId: number, categoryId: number, request: UpdateCategoryRequest) => {
      setIsLoading(true)
      setError(null)
      try {
        const response = await apiClient<CategoryResponse>(
          `/blogs/${blogId}/categories/${categoryId}`,
          {
            method: 'PUT',
            body: JSON.stringify(request),
          }
        )
        if (response.success && response.data) {
          return response.data
        } else {
          setError(response.error || { code: 'ERROR', message: 'Failed to update category' })
          return null
        }
      } catch (err: any) {
        const errorObj = { code: 'ERROR', message: err.message || 'An error occurred' }
        setError(errorObj)
        return null
      } finally {
        setIsLoading(false)
      }
    },
    []
  )

  const deleteCategory = useCallback(
    async (blogId: number, categoryId: number) => {
      setIsLoading(true)
      setError(null)
      try {
        const response = await apiClient<DeleteCategoryResponse>(
          `/blogs/${blogId}/categories/${categoryId}`,
          {
            method: 'DELETE',
          }
        )
        if (response.success && response.data) {
          return response.data
        } else {
          setError(response.error || { code: 'ERROR', message: 'Failed to delete category' })
          return null
        }
      } catch (err: any) {
        const errorObj = { code: 'ERROR', message: err.message || 'An error occurred' }
        setError(errorObj)
        return null
      } finally {
        setIsLoading(false)
      }
    },
    []
  )

  const reorderCategories = useCallback(
    async (blogId: number, request: ReorderCategoriesRequest) => {
      setIsLoading(true)
      setError(null)
      try {
        const response = await apiClient<CategoryResponse[]>(
          `/blogs/${blogId}/categories/order`,
          {
            method: 'PUT',
            body: JSON.stringify(request),
          }
        )
        if (response.success && response.data) {
          return response.data
        } else {
          setError(response.error || { code: 'ERROR', message: 'Failed to reorder categories' })
          return null
        }
      } catch (err: any) {
        const errorObj = { code: 'ERROR', message: err.message || 'An error occurred' }
        setError(errorObj)
        return null
      } finally {
        setIsLoading(false)
      }
    },
    []
  )

  return {
    categories,
    isLoading,
    error,
    getCategories,
    createCategory,
    updateCategory,
    deleteCategory,
    reorderCategories,
  }
}
