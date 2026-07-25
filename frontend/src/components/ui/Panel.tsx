import type { ReactNode } from 'react';

/** 디자인 정본 §7.5. 헤더 라벨 앞에는 항상 ■ 를 붙인다. */
export interface PanelProps {
  title?: string;
  /** 주 패널 헤더는 배경 #ffe9c9, 보조는 배경 없음. */
  tone?: 'primary' | 'secondary';
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}

export function Panel({
  title,
  tone = 'primary',
  actions,
  children,
  className = '',
}: PanelProps) {
  return (
    <section className={`border-[3px] border-ink bg-surface shadow-card ${className}`}>
      {title && (
        <header
          className={`flex items-center justify-between border-b-[3px] border-ink px-5 py-3 ${
            tone === 'primary' ? 'bg-surface-raise' : ''
          }`}
        >
          <h2 className="text-[13px] font-bold tracking-[2px]">■ {title}</h2>
          {actions}
        </header>
      )}
      {children}
    </section>
  );
}
