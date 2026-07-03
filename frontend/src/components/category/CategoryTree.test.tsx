import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import CategoryTree from './CategoryTree'
import type { CategoryTreeNode } from '../../types/category'

describe('CategoryTree', () => {
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
      children: [
        {
          categoryId: 3,
          blogId: 10,
          parentId: 2,
          name: 'Frontend',
          type: 'GENERAL',
          displayOrder: 0,
          postCount: 3,
          draftPostCount: 0,
          children: [],
        },
      ],
    },
  ]

  it('renders categories tree structure', () => {
    const mockSelect = vi.fn()
    render(
      <CategoryTree
        categories={mockCategories}
        onSelectCategory={mockSelect}
      />
    )

    expect(screen.getByText('Default')).toBeInTheDocument()
    expect(screen.getByText('Tech')).toBeInTheDocument()
    expect(screen.getByText('Frontend')).toBeInTheDocument()
  })

  it('displays post counts', () => {
    const mockSelect = vi.fn()
    render(
      <CategoryTree
        categories={mockCategories}
        onSelectCategory={mockSelect}
      />
    )

    expect(screen.getByText('(0)')).toBeInTheDocument()
    expect(screen.getByText('(5)')).toBeInTheDocument()
    expect(screen.getByText('(3)')).toBeInTheDocument()
  })

  it('displays category type badges', () => {
    const mockSelect = vi.fn()
    render(
      <CategoryTree
        categories={mockCategories}
        onSelectCategory={mockSelect}
      />
    )

    // Check that badges are rendered for different types
    const badges = screen.getAllByText(/\[.*\]/)
    expect(badges.length).toBeGreaterThan(0)
  })

  it('calls onSelectCategory when category is clicked', async () => {
    const user = userEvent.setup()
    const mockSelect = vi.fn()
    render(
      <CategoryTree
        categories={mockCategories}
        onSelectCategory={mockSelect}
      />
    )

    const techElement = screen.getByText('Tech')
    await user.click(techElement)

    expect(mockSelect).toHaveBeenCalledWith(mockCategories[1])
  })

  it('highlights selected category', () => {
    const mockSelect = vi.fn()
    render(
      <CategoryTree
        categories={mockCategories}
        selectedCategoryId={2}
        onSelectCategory={mockSelect}
      />
    )

    // Find the container div for "Tech" by going up multiple levels
    const techElement = screen.getByText('Tech').closest('[style*="margin-left"]')
    expect(techElement).toHaveClass('bg-[#0e7490]')
  })

  it('disables disabled categories', async () => {
    const user = userEvent.setup()
    const mockSelect = vi.fn()
    render(
      <CategoryTree
        categories={mockCategories}
        onSelectCategory={mockSelect}
        disabledCategoryIds={[1]}
      />
    )

    const defaultElement = screen.getByText('Default').closest('[style*="margin-left"]')
    expect(defaultElement).toHaveClass('opacity-50')
    expect(defaultElement).toHaveClass('cursor-not-allowed')

    await user.click(defaultElement!)
    expect(mockSelect).not.toHaveBeenCalled()
  })

  it('shows no categories message when empty', () => {
    const mockSelect = vi.fn()
    render(
      <CategoryTree
        categories={[]}
        onSelectCategory={mockSelect}
      />
    )

    expect(screen.getByText('No categories')).toBeInTheDocument()
  })
})
