import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router-dom'
import { AuthProvider } from '../lib/authContext'
import SettingsPostsPage from './SettingsPostsPage'
import * as useBlogSettingsModule from '../hooks/useBlogSettings'
import * as useCategoriesModule from '../hooks/useCategories'

vi.mock('../hooks/useBlogSettings')
vi.mock('../hooks/useCategories')
vi.mock('../components/layout/AppShell', () => ({
  default: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

describe('SettingsPostsPage', () => {
  const mockBlog = {
    blogId: 10,
    title: 'My Blog',
    urlSlug: 'my-blog',
    description: 'Test blog',
    isSetupCompleted: true,
    ownerId: 1,
    createdAt: '2026-07-01T00:00:00',
    updatedAt: '2026-07-01T00:00:00',
  }

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
    {
      categoryId: 2,
      blogId: 10,
      parentId: null,
      name: 'Tech',
      type: 'GENERAL' as const,
      displayOrder: 1,
      postCount: 5,
      draftPostCount: 2,
      children: [
        {
          categoryId: 3,
          blogId: 10,
          parentId: 2,
          name: 'Frontend',
          type: 'GENERAL' as const,
          displayOrder: 0,
          postCount: 3,
          draftPostCount: 0,
          children: [],
        },
      ],
    },
  ]

  beforeEach(() => {
    vi.clearAllMocks()

    vi.mocked(useBlogSettingsModule.useBlogSettings).mockReturnValue({
      blog: null,
      isLoading: false,
      error: null,
      getBlogMe: vi.fn().mockResolvedValue(mockBlog),
      updateBlog: vi.fn(),
      initialSetup: vi.fn(),
    })

    vi.mocked(useCategoriesModule.useCategories).mockReturnValue({
      categories: mockCategories,
      isLoading: false,
      error: null,
      getCategories: vi.fn().mockResolvedValue(mockCategories),
      createCategory: vi.fn(),
      updateCategory: vi.fn(),
      deleteCategory: vi.fn(),
      reorderCategories: vi.fn(),
    })
  })

  const renderPage = () => {
    return render(
      <BrowserRouter>
        <AuthProvider>
          <SettingsPostsPage />
        </AuthProvider>
      </BrowserRouter>
    )
  }

  it('loads blog and categories on mount', async () => {
    const getBlogMe = vi.fn().mockResolvedValue(mockBlog)
    const getCategories = vi.fn().mockResolvedValue(mockCategories)

    vi.mocked(useBlogSettingsModule.useBlogSettings).mockReturnValue({
      blog: null,
      isLoading: false,
      error: null,
      getBlogMe,
      updateBlog: vi.fn(),
      initialSetup: vi.fn(),
    })

    vi.mocked(useCategoriesModule.useCategories).mockReturnValue({
      categories: mockCategories,
      isLoading: false,
      error: null,
      getCategories,
      createCategory: vi.fn(),
      updateCategory: vi.fn(),
      deleteCategory: vi.fn(),
      reorderCategories: vi.fn(),
    })

    renderPage()

    await waitFor(() => {
      expect(getBlogMe).toHaveBeenCalled()
    })

    await waitFor(() => {
      expect(getCategories).toHaveBeenCalledWith(mockBlog.blogId)
    })

    expect(screen.getByText('Category Management')).toBeInTheDocument()
  })

  it('renders category tree with all categories', async () => {
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Default')).toBeInTheDocument()
    })

    expect(screen.getByText('Tech')).toBeInTheDocument()
    expect(screen.getByText('Frontend')).toBeInTheDocument()
  })

  it('displays category type badges', async () => {
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Default')).toBeInTheDocument()
    })

    // Check for badge elements
    const badges = screen.getAllByText(/\[.*\]/)
    expect(badges.length).toBeGreaterThan(0)
  })

  it('creates category when form is submitted', async () => {
    const user = userEvent.setup()
    const createCategory = vi.fn().mockResolvedValue({
      categoryId: 4,
      blogId: 10,
      parentId: null,
      name: 'Design',
      type: 'GENERAL',
      displayOrder: 2,
      postCount: 0,
      draftPostCount: 0,
    })
    const getCategories = vi.fn().mockResolvedValue(mockCategories)

    vi.mocked(useBlogSettingsModule.useBlogSettings).mockReturnValue({
      blog: mockBlog,
      isLoading: false,
      error: null,
      getBlogMe: vi.fn().mockResolvedValue(mockBlog),
      updateBlog: vi.fn(),
      initialSetup: vi.fn(),
    })

    vi.mocked(useCategoriesModule.useCategories).mockReturnValue({
      categories: mockCategories,
      isLoading: false,
      error: null,
      getCategories,
      createCategory,
      updateCategory: vi.fn(),
      deleteCategory: vi.fn(),
      reorderCategories: vi.fn(),
    })

    renderPage()

    const addButton = await screen.findByText('Add Category')
    await user.click(addButton)

    expect(screen.getByPlaceholderText('Category name')).toBeInTheDocument()

    const nameInput = screen.getByPlaceholderText('Category name')
    await user.type(nameInput, 'Design')

    const createBtn = screen.getByText('Create')
    await user.click(createBtn)

    await waitFor(() => {
      expect(createCategory).toHaveBeenCalledWith(mockBlog.blogId, {
        parentId: null,
        name: 'Design',
        type: 'GENERAL',
        displayOrder: expect.any(Number),
      })
    })
  })

  it('selects category and shows detail panel', async () => {
    const user = userEvent.setup()
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Tech')).toBeInTheDocument()
    })

    const techElement = screen.getByText('Tech')
    await user.click(techElement)

    // Detail panel should be visible
    expect(screen.getByDisplayValue('Tech')).toBeInTheDocument()
  })

  it('updates category when detail form is saved', async () => {
    const user = userEvent.setup()
    const updateCategory = vi.fn().mockResolvedValue({
      ...mockCategories[1],
      name: 'Technology',
    })
    const getCategories = vi.fn().mockResolvedValue(mockCategories)

    vi.mocked(useBlogSettingsModule.useBlogSettings).mockReturnValue({
      blog: mockBlog,
      isLoading: false,
      error: null,
      getBlogMe: vi.fn().mockResolvedValue(mockBlog),
      updateBlog: vi.fn(),
      initialSetup: vi.fn(),
    })

    vi.mocked(useCategoriesModule.useCategories).mockReturnValue({
      categories: mockCategories,
      isLoading: false,
      error: null,
      getCategories,
      createCategory: vi.fn(),
      updateCategory,
      deleteCategory: vi.fn(),
      reorderCategories: vi.fn(),
    })

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Tech')).toBeInTheDocument()
    })

    const techElement = screen.getByText('Tech')
    await user.click(techElement)

    const nameInput = screen.getByDisplayValue('Tech') as HTMLInputElement
    await user.clear(nameInput)
    await user.type(nameInput, 'Technology')

    const saveButton = screen.getByText('Save')
    await user.click(saveButton)

    await waitFor(() => {
      expect(updateCategory).toHaveBeenCalledWith(mockBlog.blogId, 2, {
        parentId: null,
        name: 'Technology',
        type: 'GENERAL',
        displayOrder: 1,
      })
    })
  })

  it('disables controls for DEFAULT category', async () => {
    const user = userEvent.setup()
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Default')).toBeInTheDocument()
    })

    const defaultElement = screen.getByText('Default')
    await user.click(defaultElement)

    const nameInput = screen.getByDisplayValue('Default') as HTMLInputElement
    expect(nameInput).toBeDisabled()

    // Delete button should not exist for DEFAULT
    expect(screen.queryByText('Delete')).not.toBeInTheDocument()
  })

  it('enables controls for GENERAL category', async () => {
    const user = userEvent.setup()
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Tech')).toBeInTheDocument()
    })

    const techElement = screen.getByText('Tech')
    await user.click(techElement)

    const nameInput = screen.getByDisplayValue('Tech') as HTMLInputElement
    expect(nameInput).not.toBeDisabled()

    // Save and Delete buttons should exist
    expect(screen.getByText('Save')).not.toBeDisabled()
    expect(screen.getByText('Delete')).toBeInTheDocument()
  })

  it('deletes category with confirmation', async () => {
    const user = userEvent.setup()
    const deleteCategory = vi.fn().mockResolvedValue({
      deletedCategoryIds: [2],
      reassignedToCategoryId: 1,
      reassignedPostCount: 0,
    })
    const getCategories = vi.fn().mockResolvedValue(mockCategories)

    vi.mocked(useBlogSettingsModule.useBlogSettings).mockReturnValue({
      blog: mockBlog,
      isLoading: false,
      error: null,
      getBlogMe: vi.fn().mockResolvedValue(mockBlog),
      updateBlog: vi.fn(),
      initialSetup: vi.fn(),
    })

    vi.mocked(useCategoriesModule.useCategories).mockReturnValue({
      categories: mockCategories,
      isLoading: false,
      error: null,
      getCategories,
      createCategory: vi.fn(),
      updateCategory: vi.fn(),
      deleteCategory,
      reorderCategories: vi.fn(),
    })

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Tech')).toBeInTheDocument()
    })

    const techElement = screen.getByText('Tech')
    await user.click(techElement)

    const deleteButton = screen.getByText('Delete')
    await user.click(deleteButton)

    // Confirmation modal should appear
    expect(screen.getByText(/Are you sure you want to delete/)).toBeInTheDocument()

    const confirmButton = screen.getByText('Confirm Delete')
    await user.click(confirmButton)

    await waitFor(() => {
      expect(deleteCategory).toHaveBeenCalledWith(mockBlog.blogId, 2)
    })
  })

  it('reorders categories when move up/down is clicked', async () => {
    const user = userEvent.setup()
    const reorderCategories = vi.fn().mockResolvedValue([
      { ...mockCategories[1], displayOrder: 0 },
      { ...mockCategories[0], displayOrder: 1 },
    ])
    const getCategories = vi.fn().mockResolvedValue(mockCategories)

    vi.mocked(useBlogSettingsModule.useBlogSettings).mockReturnValue({
      blog: mockBlog,
      isLoading: false,
      error: null,
      getBlogMe: vi.fn().mockResolvedValue(mockBlog),
      updateBlog: vi.fn(),
      initialSetup: vi.fn(),
    })

    vi.mocked(useCategoriesModule.useCategories).mockReturnValue({
      categories: mockCategories,
      isLoading: false,
      error: null,
      getCategories,
      createCategory: vi.fn(),
      updateCategory: vi.fn(),
      deleteCategory: vi.fn(),
      reorderCategories,
    })

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Tech')).toBeInTheDocument()
    })

    const techElement = screen.getByText('Tech')
    await user.click(techElement)

    const moveUpButton = screen.getByText('Move Up')
    await user.click(moveUpButton)

    await waitFor(() => {
      expect(reorderCategories).toHaveBeenCalledWith(mockBlog.blogId, {
        parentId: null,
        orderedCategoryIds: [2, 1],
      })
    })
  })

  it('displays error message from API', async () => {
    const errorMessage = 'Cannot reorder locked category'

    vi.mocked(useCategoriesModule.useCategories).mockReturnValue({
      categories: mockCategories,
      isLoading: false,
      error: { code: 'CAT_005', message: errorMessage },
      getCategories: vi.fn().mockResolvedValue(mockCategories),
      createCategory: vi.fn(),
      updateCategory: vi.fn(),
      deleteCategory: vi.fn(),
      reorderCategories: vi.fn(),
    })

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('CAT_005')).toBeInTheDocument()
    })

    expect(screen.getByText(errorMessage)).toBeInTheDocument()
  })

  it('shows loading state initially', async () => {
    vi.mocked(useBlogSettingsModule.useBlogSettings).mockReturnValue({
      blog: null,
      isLoading: true,
      error: null,
      getBlogMe: vi.fn(),
      updateBlog: vi.fn(),
      initialSetup: vi.fn(),
    })

    renderPage()

    expect(screen.getByText('로딩 중...')).toBeInTheDocument()
  })
})
