import { useState, useEffect } from 'react'
import Button from '../ui/Button'
import type { CategoryTreeNode, CategoryType } from '../../types/category'

interface CategoryDetailPanelProps {
  category: CategoryTreeNode | null
  allCategories: CategoryTreeNode[]
  onSave: (data: {
    parentId: number | null
    name: string
    type: CategoryType
    displayOrder: number
  }) => Promise<void>
  onDelete: () => Promise<void>
  isLoading?: boolean
  error?: { code: string; message: string } | null
}

export default function CategoryDetailPanel({
  category,
  allCategories,
  onSave,
  onDelete,
  isLoading = false,
  error,
}: CategoryDetailPanelProps) {
  const [name, setName] = useState('')
  const [type, setType] = useState<CategoryType>('GENERAL')
  const [displayOrder, setDisplayOrder] = useState(0)
  const [parentId, setParentId] = useState<number | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  useEffect(() => {
    if (category) {
      setName(category.name)
      setType(category.type)
      setDisplayOrder(category.displayOrder)
      setParentId(category.parentId)
    }
  }, [category])

  if (!category) {
    return (
      <div className="text-center text-text-muted">
        <p>Select a category to edit</p>
      </div>
    )
  }

  const isImmutable = category.type === 'DEFAULT' || category.type === 'LOCKED'

  const handleSave = async () => {
    if (!name.trim()) {
      return
    }
    setIsSaving(true)
    try {
      await onSave({
        parentId,
        name: name.trim(),
        type,
        displayOrder,
      })
    } finally {
      setIsSaving(false)
    }
  }

  const handleDelete = async () => {
    setIsDeleting(true)
    try {
      await onDelete()
      setShowDeleteConfirm(false)
    } finally {
      setIsDeleting(false)
    }
  }

  // Filter available parents: must be a root category from same blog
  const availableParents = allCategories.filter(
    (cat) => cat.parentId === null && cat.categoryId !== category.categoryId
  )

  return (
    <div className="space-y-4">
      {error && (
        <div className="p-3 bg-[#3b0718] border-2 border-border-danger rounded text-text-primary text-sm">
          <p className="font-semibold">{error.code}</p>
          <p>{error.message}</p>
        </div>
      )}

      <div>
        <label className="block text-text-primary font-sans text-sm mb-2">Category Name</label>
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          disabled={isImmutable}
          className="w-full px-3 py-2 bg-[#030712] border-2 border-border-purple-dark text-text-primary disabled:opacity-50 disabled:cursor-not-allowed"
        />
      </div>

      <div>
        <label className="block text-text-primary font-sans text-sm mb-2">Type</label>
        <select
          value={type}
          onChange={(e) => setType(e.target.value as CategoryType)}
          disabled={isImmutable}
          className="w-full px-3 py-2 bg-[#030712] border-2 border-border-purple-dark text-text-primary disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <option value="GENERAL">General</option>
          <option value="LOCKED">Locked</option>
        </select>
      </div>

      <div>
        <label className="block text-text-primary font-sans text-sm mb-2">Parent Category</label>
        <select
          value={parentId ?? ''}
          onChange={(e) => setParentId(e.target.value ? Number(e.target.value) : null)}
          disabled={isImmutable}
          className="w-full px-3 py-2 bg-[#030712] border-2 border-border-purple-dark text-text-primary disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <option value="">None (Root)</option>
          {availableParents.map((cat) => (
            <option key={cat.categoryId} value={cat.categoryId}>
              {cat.name}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-text-primary font-sans text-sm mb-2">Display Order</label>
        <input
          type="number"
          value={displayOrder}
          onChange={(e) => setDisplayOrder(Number(e.target.value))}
          disabled={isImmutable}
          min="0"
          className="w-full px-3 py-2 bg-[#030712] border-2 border-border-purple-dark text-text-primary disabled:opacity-50 disabled:cursor-not-allowed"
        />
      </div>

      <div className="flex gap-2">
        <Button
          onClick={handleSave}
          disabled={isImmutable || isSaving || isLoading}
          variant="primary"
        >
          {isSaving ? 'Saving...' : 'Save'}
        </Button>

        {!isImmutable && (
          <Button
            onClick={() => setShowDeleteConfirm(true)}
            disabled={isDeleting || isLoading}
            variant="danger"
          >
            Delete
          </Button>
        )}
      </div>

      {showDeleteConfirm && (
        <div className="p-4 bg-[#3b0718] border-2 border-border-danger rounded">
          <p className="text-text-primary mb-3">
            Are you sure you want to delete this category and all its children?
          </p>
          <div className="flex gap-2">
            <Button onClick={handleDelete} disabled={isDeleting} variant="danger">
              {isDeleting ? 'Deleting...' : 'Confirm Delete'}
            </Button>
            <Button
              onClick={() => setShowDeleteConfirm(false)}
              disabled={isDeleting}
              variant="neutral"
            >
              Cancel
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
