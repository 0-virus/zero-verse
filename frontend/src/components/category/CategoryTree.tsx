import type { CategoryTreeNode } from '../../types/category'
import CategoryTypeBadge from './CategoryTypeBadge'

interface CategoryTreeProps {
  categories: CategoryTreeNode[]
  selectedCategoryId?: number
  onSelectCategory: (category: CategoryTreeNode) => void
  disabledCategoryIds?: number[]
}

function renderTreeNode(
  node: CategoryTreeNode,
  level: number,
  selectedId?: number,
  onSelect?: (cat: CategoryTreeNode) => void,
  disabled?: Set<number>
) {
  const isSelected = node.categoryId === selectedId
  const isDisabled = disabled?.has(node.categoryId) ?? false
  const indent = level * 20

  return (
    <div key={node.categoryId}>
      <div
        onClick={() => !isDisabled && onSelect?.(node)}
        className={`
          px-3 py-2 mb-1 cursor-pointer border-2 rounded transition
          ${
            isSelected
              ? 'bg-[#0e7490] border-border-cyan-dark shadow-card'
              : 'bg-[#070817] border-border-purple-dark hover:bg-[#0f1029]'
          }
          ${isDisabled ? 'opacity-50 cursor-not-allowed' : ''}
        `}
        style={{ marginLeft: `${indent}px` }}
      >
        <div className="flex items-center justify-between gap-2">
          <div className="flex-1 flex items-center gap-2 min-w-0">
            <span className="text-text-primary font-sans text-sm truncate">{node.name}</span>
            <CategoryTypeBadge type={node.type} />
          </div>
          <span className="text-text-muted text-xs whitespace-nowrap">
            ({node.postCount})
          </span>
        </div>
      </div>
      {node.children &&
        node.children.map((child) =>
          renderTreeNode(child, level + 1, selectedId, onSelect, disabled)
        )}
    </div>
  )
}

export default function CategoryTree({
  categories,
  selectedCategoryId,
  onSelectCategory,
  disabledCategoryIds = [],
}: CategoryTreeProps) {
  const disabledSet = new Set(disabledCategoryIds)

  if (categories.length === 0) {
    return <p className="text-text-muted text-sm">No categories</p>
  }

  return (
    <div className="space-y-1">
      {categories.map((cat) =>
        renderTreeNode(cat, 0, selectedCategoryId, onSelectCategory, disabledSet)
      )}
    </div>
  )
}
