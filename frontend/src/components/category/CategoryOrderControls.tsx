import { useState } from 'react'
import Button from '../ui/Button'
import type { CategoryTreeNode } from '../../types/category'

interface CategoryOrderControlsProps {
  categories: CategoryTreeNode[]
  selectedCategoryId?: number
  onReorder: (parentId: number | null, orderedIds: number[]) => Promise<void>
  isLoading?: boolean
  error?: { code: string; message: string } | null
}

export default function CategoryOrderControls({
  categories,
  selectedCategoryId,
  onReorder,
  isLoading = false,
  error,
}: CategoryOrderControlsProps) {
  const [isReordering, setIsReordering] = useState(false)

  if (!selectedCategoryId) {
    return <p className="text-text-muted text-sm">Select a category to view order options</p>
  }

  // Find selected category and determine its siblings
  const findCategory = (cats: CategoryTreeNode[], id: number): CategoryTreeNode | null => {
    for (const cat of cats) {
      if (cat.categoryId === id) return cat
      if (cat.children) {
        const found = findCategory(cat.children, id)
        if (found) return found
      }
    }
    return null
  }

  const findSiblings = (
    cats: CategoryTreeNode[],
    id: number
  ): { siblings: CategoryTreeNode[]; parentId: number | null } | null => {
    // Check root level
    const rootSiblings = cats.filter((cat) => cat.parentId === null)
    if (rootSiblings.some((cat) => cat.categoryId === id)) {
      return { siblings: rootSiblings, parentId: null }
    }

    // Check children
    for (const cat of cats) {
      if (cat.children) {
        const childSiblings = cat.children.filter((c) => c.parentId === cat.categoryId)
        if (childSiblings.some((c) => c.categoryId === id)) {
          return { siblings: childSiblings, parentId: cat.categoryId }
        }

        // Recurse
        const found = findSiblings(cat.children, id)
        if (found) return found
      }
    }

    return null
  }

  const selected = findCategory(categories, selectedCategoryId)
  const siblingInfo = findSiblings(categories, selectedCategoryId)

  if (!selected || !siblingInfo) {
    return null
  }

  const { siblings, parentId } = siblingInfo
  const currentIndex = siblings.findIndex((cat) => cat.categoryId === selectedCategoryId)

  const handleMoveUp = async () => {
    if (currentIndex <= 0) return

    const newOrder = [...siblings]
    ;[newOrder[currentIndex - 1], newOrder[currentIndex]] = [
      newOrder[currentIndex],
      newOrder[currentIndex - 1],
    ]

    setIsReordering(true)
    try {
      await onReorder(
        parentId,
        newOrder.map((cat) => cat.categoryId)
      )
    } finally {
      setIsReordering(false)
    }
  }

  const handleMoveDown = async () => {
    if (currentIndex >= siblings.length - 1) return

    const newOrder = [...siblings]
    ;[newOrder[currentIndex + 1], newOrder[currentIndex]] = [
      newOrder[currentIndex],
      newOrder[currentIndex + 1],
    ]

    setIsReordering(true)
    try {
      await onReorder(
        parentId,
        newOrder.map((cat) => cat.categoryId)
      )
    } finally {
      setIsReordering(false)
    }
  }

  return (
    <div className="space-y-3">
      {error && (
        <div className="p-3 bg-[#3b0718] border-2 border-border-danger rounded text-text-primary text-sm">
          <p className="font-semibold">{error.code}</p>
          <p>{error.message}</p>
        </div>
      )}

      <div>
        <label className="block text-text-primary font-sans text-sm mb-2">
          Position: {currentIndex + 1} of {siblings.length}
        </label>
        <div className="flex gap-2">
          <Button
            onClick={handleMoveUp}
            disabled={currentIndex <= 0 || isReordering || isLoading}
            variant="neutral"
          >
            Move Up
          </Button>
          <Button
            onClick={handleMoveDown}
            disabled={currentIndex >= siblings.length - 1 || isReordering || isLoading}
            variant="neutral"
          >
            Move Down
          </Button>
        </div>
      </div>
    </div>
  )
}
