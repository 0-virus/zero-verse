import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import CategoryDetailPanel from './CategoryDetailPanel'
import type { CategoryTreeNode } from '../../types/category'

describe('CategoryDetailPanel', () => {
  const mockCategory: CategoryTreeNode = {
    categoryId: 2,
    blogId: 10,
    parentId: null,
    name: 'Tech',
    type: 'GENERAL',
    displayOrder: 1,
    postCount: 5,
    draftPostCount: 2,
    children: [],
  }

  const mockLockedCategory: CategoryTreeNode = {
    categoryId: 1,
    blogId: 10,
    parentId: null,
    name: 'Default',
    type: 'LOCKED',
    displayOrder: 0,
    postCount: 0,
    draftPostCount: 0,
    children: [],
  }

  const allCategories = [mockLockedCategory, mockCategory]

  it('shows select category message when no category', () => {
    const mockSave = vi.fn()
    const mockDelete = vi.fn()

    render(
      <CategoryDetailPanel
        category={null}
        allCategories={allCategories}
        onSave={mockSave}
        onDelete={mockDelete}
      />
    )

    expect(screen.getByText('Select a category to edit')).toBeInTheDocument()
  })

  it('displays category details when selected', () => {
    const mockSave = vi.fn()
    const mockDelete = vi.fn()

    render(
      <CategoryDetailPanel
        category={mockCategory}
        allCategories={allCategories}
        onSave={mockSave}
        onDelete={mockDelete}
      />
    )

    expect(screen.getByDisplayValue('Tech')).toBeInTheDocument()
    expect(screen.getByDisplayValue('1')).toBeInTheDocument()
  })

  it('disables controls for DEFAULT category', () => {
    const mockSave = vi.fn()
    const mockDelete = vi.fn()

    const defaultCategory: CategoryTreeNode = {
      categoryId: 1,
      blogId: 10,
      parentId: null,
      name: 'Default',
      type: 'DEFAULT',
      displayOrder: 0,
      postCount: 0,
      draftPostCount: 0,
      children: [],
    }

    render(
      <CategoryDetailPanel
        category={defaultCategory}
        allCategories={[defaultCategory]}
        onSave={mockSave}
        onDelete={mockDelete}
      />
    )

    const nameInput = screen.getByDisplayValue('Default')
    const saveButton = screen.getByText('Save')

    expect(nameInput).toBeDisabled()
    expect(saveButton).toBeDisabled()
    expect(screen.queryByText('Delete')).not.toBeInTheDocument()
  })

  it('disables controls for LOCKED category', () => {
    const mockSave = vi.fn()
    const mockDelete = vi.fn()

    render(
      <CategoryDetailPanel
        category={mockLockedCategory}
        allCategories={allCategories}
        onSave={mockSave}
        onDelete={mockDelete}
      />
    )

    const nameInput = screen.getByDisplayValue('Default')
    expect(nameInput).toBeDisabled()
    expect(screen.queryByText('Delete')).not.toBeInTheDocument()
  })

  it('enables controls for GENERAL category', () => {
    const mockSave = vi.fn()
    const mockDelete = vi.fn()

    render(
      <CategoryDetailPanel
        category={mockCategory}
        allCategories={allCategories}
        onSave={mockSave}
        onDelete={mockDelete}
      />
    )

    const nameInput = screen.getByDisplayValue('Tech')
    expect(nameInput).not.toBeDisabled()
    expect(screen.getByText('Save')).not.toBeDisabled()
    expect(screen.getByText('Delete')).not.toBeDisabled()
  })

  it('calls onSave with correct data', async () => {
    const user = userEvent.setup()
    const mockSave = vi.fn().mockResolvedValue(undefined)
    const mockDelete = vi.fn()

    render(
      <CategoryDetailPanel
        category={mockCategory}
        allCategories={allCategories}
        onSave={mockSave}
        onDelete={mockDelete}
      />
    )

    const nameInput = screen.getByDisplayValue('Tech') as HTMLInputElement
    await user.clear(nameInput)
    await user.type(nameInput, 'NewTech')

    await user.click(screen.getByText('Save'))

    expect(mockSave).toHaveBeenCalledWith({
      parentId: null,
      name: 'NewTech',
      type: 'GENERAL',
      displayOrder: 1,
    })
  })

  it('shows delete confirmation modal', async () => {
    const user = userEvent.setup()
    const mockSave = vi.fn()
    const mockDelete = vi.fn()

    render(
      <CategoryDetailPanel
        category={mockCategory}
        allCategories={allCategories}
        onSave={mockSave}
        onDelete={mockDelete}
      />
    )

    await user.click(screen.getByText('Delete'))

    expect(screen.getByText(/Are you sure you want to delete/)).toBeInTheDocument()
  })

  it('calls onDelete when confirmed', async () => {
    const user = userEvent.setup()
    const mockSave = vi.fn()
    const mockDelete = vi.fn().mockResolvedValue(undefined)

    render(
      <CategoryDetailPanel
        category={mockCategory}
        allCategories={allCategories}
        onSave={mockSave}
        onDelete={mockDelete}
      />
    )

    await user.click(screen.getByText('Delete'))
    await user.click(screen.getByText('Confirm Delete'))

    expect(mockDelete).toHaveBeenCalled()
  })

  it('displays error message', () => {
    const mockSave = vi.fn()
    const mockDelete = vi.fn()

    render(
      <CategoryDetailPanel
        category={mockCategory}
        allCategories={allCategories}
        onSave={mockSave}
        onDelete={mockDelete}
        error={{ code: 'CAT_004', message: 'Duplicate name' }}
      />
    )

    expect(screen.getByText('CAT_004')).toBeInTheDocument()
    expect(screen.getByText('Duplicate name')).toBeInTheDocument()
  })
})
