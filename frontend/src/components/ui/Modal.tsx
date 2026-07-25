import type { ReactNode } from 'react';

export interface ModalProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
}

export function Modal({ open, title, onClose, children, footer }: ModalProps) {
  if (!open) return null;

  return (
    <div
      role="presentation"
      onClick={onClose}
      className="fixed inset-0 z-50 flex items-center justify-center bg-[rgba(43,27,61,0.5)]"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
        className="w-[520px] border-[3px] border-ink bg-surface shadow-panel"
      >
        <header className="flex items-center justify-between border-b-[3px] border-ink bg-surface-raise px-5 py-3">
          <h2 className="text-[13px] font-bold tracking-[2px]">■ {title}</h2>
          <button
            type="button"
            aria-label="닫기"
            onClick={onClose}
            className="cursor-pointer text-[13px] font-bold"
          >
            ×
          </button>
        </header>
        <div className="px-5 py-4 text-[13px] text-text-body">{children}</div>
        {footer && <footer className="flex justify-end gap-2 px-5 pb-4">{footer}</footer>}
      </div>
    </div>
  );
}
