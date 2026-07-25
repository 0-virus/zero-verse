import type { ReactNode } from 'react';

/** 디자인 정본 §7.8. */
export interface ListRowProps {
  avatar?: ReactNode;
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}

export function ListRow({ avatar, title, subtitle, actions }: ListRowProps) {
  return (
    <div className="flex items-center gap-3 border-b border-line px-5 py-3.5 hover:bg-surface-soft">
      {avatar}
      <div className="min-w-0 flex-1">
        <p className="truncate text-[13.5px] font-bold">{title}</p>
        {subtitle && <p className="truncate text-xs text-text-muted">{subtitle}</p>}
      </div>
      {actions && <div className="flex shrink-0 gap-2">{actions}</div>}
    </div>
  );
}
