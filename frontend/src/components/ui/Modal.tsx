import type { ReactNode } from 'react'

interface ModalProps {
  isOpen: boolean
  onClose: () => void
  title: string
  children: ReactNode
}

export default function Modal({ isOpen, onClose, title, children }: ModalProps) {
  if (!isOpen) return null

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-bg-panel border-4 border-border-cyan-dark p-6 shadow-card max-w-md w-full">
        <div className="flex justify-between items-center mb-4">
          <h2 className="font-display text-text-primary">{title}</h2>
          <button
            onClick={onClose}
            className="text-text-muted hover:text-text-primary text-xl"
          >
            ×
          </button>
        </div>
        <div>{children}</div>
      </div>
    </div>
  )
}
