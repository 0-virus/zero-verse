import type { CategoryType } from '../../types/category'

interface CategoryTypeBadgeProps {
  type: CategoryType
}

export default function CategoryTypeBadge({ type }: CategoryTypeBadgeProps) {
  const getTypeConfig = (categoryType: CategoryType) => {
    switch (categoryType) {
      case 'DEFAULT':
        return {
          bgClass: 'bg-[#111827]',
          borderClass: 'border-border-purple-dark',
          text: '미분류',
        }
      case 'GENERAL':
        return {
          bgClass: 'bg-[#0e7490]',
          borderClass: 'border-border-cyan-dark',
          text: '일반',
        }
      case 'LOCKED':
        return {
          bgClass: 'bg-[#3b0718]',
          borderClass: 'border-border-danger',
          text: '잠금',
        }
      default:
        return {
          bgClass: 'bg-[#111827]',
          borderClass: 'border-border-purple-dark',
          text: 'Unknown',
        }
    }
  }

  const config = getTypeConfig(type)

  return (
    <span
      className={`inline-block px-2 py-1 text-xs font-sans border border-2 rounded text-text-primary ${config.bgClass} ${config.borderClass}`}
    >
      [{config.text}]
    </span>
  )
}
