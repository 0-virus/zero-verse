import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import CategoryOrderControls from './CategoryOrderControls'
import type { CategoryTreeNode } from '../../types/category'

describe('CategoryOrderControls', () => {
  const mockCategories: CategoryTreeNode[] = [
    {
      categoryId: 1,
      blogId: 10,
      parentId: null,
      name: 'Default',
      type: 'DEFAULT',
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
      type: 'GENERAL',
      displayOrder: 1,
      postCount: 5,
      draftPostCount: 2,
      children: [],
    },
    {
      categoryId: 3,
      blogId: 10,
      parentId: null,
      name: 'Design',
      type: 'GENERAL',
      displayOrder: 2,
      postCount: 3,
      draftPostCount: 0,
      children: [],
    },
  ]

  it('shows message when no category selected', () => {
    const mockReorder = vi.fn()
    render(
      <CategoryOrderControls
        categories={mockCategories}
        onReorder={mockReorder}
      />
    )

    expect(screen.getByText(/Select a category to view order options/)).toBeInTheDocument()
  })

  it('displays position info', () => {
    const mockReorder = vi.fn()
    render(
      <CategoryOrderControls
        categories={mockCategories}
        selectedCategoryId={2}
        onReorder={mockReorder}
      />
    )

    expect(screen.getByText('Position: 2 of 3')).toBeInTheDocument()
  })

  it('disables move up for first category', () => {
    const mockReorder = vi.fn()
    render(
      <CategoryOrderControls
        categories={mockCategories}
        selectedCategoryId={1}
        onReorder={mockReorder}
      />
    )

    const moveUpButton = screen.getByText('Move Up')
    expect(moveUpButton).toBeDisabled()
  })

  it('disables move down for last category', () => {
    const mockReorder = vi.fn()
    render(
      <CategoryOrderControls
        categories={mockCategories}
        selectedCategoryId={3}
        onReorder={mockReorder}
      />
    )

    const moveDownButton = screen.getByText('Move Down')
    expect(moveDownButton).toBeDisabled()
  })

  it('calls onReorder with correct order when moving up', async () => {
    const user = userEvent.setup()
    const mockReorder = vi.fn().mockResolvedValue(undefined)
    render(
      <CategoryOrderControls
        categories={mockCategories}
        selectedCategoryId={2}
        onReorder={mockReorder}
      />
    )

    await user.click(screen.getByText('Move Up'))

    expect(mockReorder).toHaveBeenCalledWith(null, [2, 1, 3])
  })

  it('calls onReorder with correct order when moving down', async () => {
    const user = userEvent.setup()
    const mockReorder = vi.fn().mockResolvedValue(undefined)
    render(
      <CategoryOrderControls
        categories={mockCategories}
        selectedCategoryId={2}
        onReorder={mockReorder}
      />
    )

    await user.click(screen.getByText('Move Down'))

    expect(mockReorder).toHaveBeenCalledWith(null, [1, 3, 2])
  })

  it('displays error message', () => {
    const mockReorder = vi.fn()
    render(
      <CategoryOrderControls
        categories={mockCategories}
        selectedCategoryId={2}
        onReorder={mockReorder}
        error={{ code: 'CAT_005', message: 'Cannot reorder locked category' }}
      />
    )

    expect(screen.getByText('CAT_005')).toBeInTheDocument()
    expect(screen.getByText('Cannot reorder locked category')).toBeInTheDocument()
  })

  it('handles child categories with parent', async () => {
    const user = userEvent.setup()
    const categoriesWithChildren: CategoryTreeNode[] = [
      {
        categoryId: 1,
        blogId: 10,
        parentId: null,
        name: 'Tech',
        type: 'GENERAL',
        displayOrder: 0,
        postCount: 0,
        draftPostCount: 0,
        children: [
          {
            categoryId: 2,
            blogId: 10,
            parentId: 1,
            name: 'Frontend',
            type: 'GENERAL',
            displayOrder: 0,
            postCount: 0,
            draftPostCount: 0,
            children: [],
          },
          {
            categoryId: 3,
            blogId: 10,
            parentId: 1,
            name: 'Backend',
            type: 'GENERAL',
            displayOrder: 1,
            postCount: 0,
            draftPostCount: 0,
            children: [],
          },
        ],
      },
    ]

    const mockReorder = vi.fn().mockResolvedValue(undefined)
    render(
      <CategoryOrderControls
        categories={categoriesWithChildren}
        selectedCategoryId={2}
        onReorder={mockReorder}
      />
    )

    expect(screen.getByText('Position: 1 of 2')).toBeInTheDocument()

    await user.click(screen.getByText('Move Down'))

    expect(mockReorder).toHaveBeenCalledWith(1, [3, 2])
  })

  it('disables move buttons for LOCKED category', () => {
    const mockReorder = vi.fn()
    const lockedCategories: CategoryTreeNode[] = [
      {
        categoryId: 1,
        blogId: 10,
        parentId: null,
        name: 'Default',
        type: 'DEFAULT',
        displayOrder: 0,
        postCount: 0,
        draftPostCount: 0,
        children: [],
      },
      {
        categoryId: 2,
        blogId: 10,
        parentId: null,
        name: 'LockedCat',
        type: 'LOCKED',
        displayOrder: 1,
        postCount: 0,
        draftPostCount: 0,
        children: [],
      },
    ]

    render(
      <CategoryOrderControls
        categories={lockedCategories}
        selectedCategoryId={2}
        onReorder={mockReorder}
      />
    )

    expect(screen.getByText('LOCKED categories cannot be reordered')).toBeInTheDocument()
    expect(screen.queryByText('Move Up')).not.toBeInTheDocument()
    expect(screen.queryByText('Move Down')).not.toBeInTheDocument()
  })

  it('allows move buttons for DEFAULT category', () => {
    const mockReorder = vi.fn()
    const categoriesWithDefault: CategoryTreeNode[] = [
      {
        categoryId: 1,
        blogId: 10,
        parentId: null,
        name: 'Default',
        type: 'DEFAULT',
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
        type: 'GENERAL',
        displayOrder: 1,
        postCount: 0,
        draftPostCount: 0,
        children: [],
      },
    ]

    render(
      <CategoryOrderControls
        categories={categoriesWithDefault}
        selectedCategoryId={1}
        onReorder={mockReorder}
      />
    )

    // DEFAULT should allow move buttons - specifically Move Down should be enabled
    expect(screen.getByText('Move Down')).not.toBeDisabled()
  })
})
