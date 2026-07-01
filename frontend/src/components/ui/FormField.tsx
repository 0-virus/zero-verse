import type { ReactNode } from 'react'

interface FormFieldProps {
  label: string
  error?: string
  children: ReactNode
  className?: string
}

export default function FormField({ label, error, children, className = '' }: FormFieldProps) {
  return (
    <div className={`space-y-1 ${className}`}>
      <label className="block text-text-primary text-sm font-semibold">{label}</label>
      {children}
      {error && <p className="text-border-danger text-xs">{error}</p>}
    </div>
  )
}
