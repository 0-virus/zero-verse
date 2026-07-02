import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useCategories } from './useCategories'
import { apiClient } from '../lib/apiClient'

vi.mock('../lib/apiClient')

describe('useCategories', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should fetch categories successfully', async () => {
    const mockCategories = [
      {
        categoryId: 1,
        blogId: 10,
        parentId: null,
        name: 'Default',
        type: 'DEFAULT' as const,
        displayOrder: 0,
        postCount: 0,
        draftPostCount: 0,
        children: [],
      },
    ]

    vi.mocked(apiClient).mockResolvedValueOnce({
      success: true,
      data: mockCategories,
      timestamp: new Date().toISOString(),
    })

    const { result } = renderHook(() => useCategories())

    const categories = await result.current.getCategories(10)

    await waitFor(() => {
      expect(result.current.categories).toEqual(mockCategories)
    })

    expect(categories).toEqual(mockCategories)
  })

  it('should handle fetch error', async () => {
    vi.mocked(apiClient).mockResolvedValueOnce({
      success: false,
      error: { code: 'ERROR', message: 'Failed to fetch' },
      timestamp: new Date().toISOString(),
    })

    const { result } = renderHook(() => useCategories())

    const categories = await result.current.getCategories(10)

    await waitFor(() => {
      expect(result.current.error).toEqual({ code: 'ERROR', message: 'Failed to fetch' })
    })

    expect(categories).toBeNull()
  })

  it('should create category successfully', async () => {
    const mockCategory = {
      categoryId: 2,
      blogId: 10,
      parentId: null,
      name: 'Tech',
      type: 'GENERAL' as const,
      displayOrder: 1,
      postCount: 0,
      draftPostCount: 0,
      createdAt: '2026-07-02T00:00:00',
      updatedAt: '2026-07-02T00:00:00',
    }

    vi.mocked(apiClient).mockResolvedValueOnce({
      success: true,
      data: mockCategory,
      timestamp: new Date().toISOString(),
    })

    const { result } = renderHook(() => useCategories())

    const category = await result.current.createCategory(10, {
      parentId: null,
      name: 'Tech',
      type: 'GENERAL',
      displayOrder: 1,
    })

    expect(category).toEqual(mockCategory)
  })

  it('should update category successfully', async () => {
    const mockCategory = {
      categoryId: 2,
      blogId: 10,
      parentId: null,
      name: 'Technology',
      type: 'GENERAL' as const,
      displayOrder: 1,
      postCount: 0,
      draftPostCount: 0,
      createdAt: '2026-07-02T00:00:00',
      updatedAt: '2026-07-02T01:00:00',
    }

    vi.mocked(apiClient).mockResolvedValueOnce({
      success: true,
      data: mockCategory,
      timestamp: new Date().toISOString(),
    })

    const { result } = renderHook(() => useCategories())

    const category = await result.current.updateCategory(10, 2, {
      parentId: null,
      name: 'Technology',
      type: 'GENERAL',
      displayOrder: 1,
    })

    expect(category).toEqual(mockCategory)
  })

  it('should delete category successfully', async () => {
    const mockResponse = {
      deletedCategoryIds: [2],
      reassignedToCategoryId: 1,
      reassignedPostCount: 0,
    }

    vi.mocked(apiClient).mockResolvedValueOnce({
      success: true,
      data: mockResponse,
      timestamp: new Date().toISOString(),
    })

    const { result } = renderHook(() => useCategories())

    const response = await result.current.deleteCategory(10, 2)

    expect(response).toEqual(mockResponse)
  })

  it('should reorder categories successfully', async () => {
    const mockCategories = [
      {
        categoryId: 1,
        blogId: 10,
        parentId: null,
        name: 'Default',
        type: 'DEFAULT' as const,
        displayOrder: 0,
        postCount: 0,
        draftPostCount: 0,
      },
      {
        categoryId: 2,
        blogId: 10,
        parentId: null,
        name: 'Tech',
        type: 'GENERAL' as const,
        displayOrder: 1,
        postCount: 0,
        draftPostCount: 0,
      },
    ]

    vi.mocked(apiClient).mockResolvedValueOnce({
      success: true,
      data: mockCategories,
      timestamp: new Date().toISOString(),
    })

    const { result } = renderHook(() => useCategories())

    const categories = await result.current.reorderCategories(10, {
      parentId: null,
      orderedCategoryIds: [1, 2],
    })

    expect(categories).toEqual(mockCategories)
  })

  it('should handle reorder error', async () => {
    vi.mocked(apiClient).mockResolvedValueOnce({
      success: false,
      error: { code: 'CAT_005', message: 'Cannot reorder locked category' },
      timestamp: new Date().toISOString(),
    })

    const { result } = renderHook(() => useCategories())

    const categories = await result.current.reorderCategories(10, {
      parentId: null,
      orderedCategoryIds: [1, 2],
    })

    await waitFor(() => {
      expect(result.current.error?.code).toBe('CAT_005')
    })

    expect(categories).toBeNull()
  })
})
