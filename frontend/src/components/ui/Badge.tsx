export type Visibility = 'PUBLIC' | 'UNIVERSE' | 'PRIVATE'

interface BadgeProps {
  visibility: Visibility
  className?: string
}

const visibilityConfig: Record<Visibility, { label: string; bgClass: string; borderClass: string; textClass: string }> = {
  PUBLIC: {
    label: 'Public',
    bgClass: 'bg-transparent',
    borderClass: 'border-accent-green',
    textClass: 'text-accent-green',
  },
  UNIVERSE: {
    label: '친구',
    bgClass: 'bg-transparent',
    borderClass: 'border-border-cyan-dark',
    textClass: 'text-border-cyan-dark',
  },
  PRIVATE: {
    label: 'Private',
    bgClass: 'bg-transparent',
    borderClass: 'border-border-purple-dark',
    textClass: 'text-border-purple-dark',
  },
}

export default function Badge({ visibility, className = '' }: BadgeProps) {
  const config = visibilityConfig[visibility]

  return (
    <span
      className={`inline-block px-2 py-1 border-2 text-xs font-semibold ${config.bgClass} ${config.borderClass} ${config.textClass} ${className}`}
    >
      {config.label}
    </span>
  )
}
