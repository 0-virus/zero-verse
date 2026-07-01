import type { ReactNode } from 'react'

type ButtonVariant = 'primary' | 'neutral' | 'danger' | 'gold'

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  children: ReactNode
}

const variantConfig: Record<ButtonVariant, { bgClass: string; borderClass: string; shadowClass: string }> = {
  primary: {
    bgClass: 'bg-bg-button-primary',
    borderClass: 'border-border-cyan-dark',
    shadowClass: 'shadow-button',
  },
  neutral: {
    bgClass: 'bg-bg-button-neutral',
    borderClass: 'border-border-purple-dark',
    shadowClass: 'shadow-button',
  },
  danger: {
    bgClass: 'bg-bg-button-danger',
    borderClass: 'border-border-danger',
    shadowClass: 'shadow-button',
  },
  gold: {
    bgClass: 'bg-bg-button-neutral',
    borderClass: 'border-border-admin',
    shadowClass: 'shadow-button',
  },
}

export default function Button({
  variant = 'primary',
  className = '',
  children,
  ...props
}: ButtonProps) {
  const config = variantConfig[variant]

  return (
    <button
      className={`px-4 py-2 border-2 text-text-primary hover:bg-opacity-80 transition ${config.bgClass} ${config.borderClass} ${config.shadowClass} ${className}`}
      {...props}
    >
      {children}
    </button>
  )
}
