import type { ReactNode } from 'react';

/**
 * 디자인 정본 §7.5. 헤더 라벨 앞에는 항상 ■ 를 붙인다.
 *
 * 헤더 규격은 정본 인스턴스(`Main Feed v2.dc.html` RIGHT RAIL, `Pages.dc.html` 블로그 카테고리)를
 * 따른다: `padding:11px 14px; font-size:12px; font-weight:700; letter-spacing:3px`,
 * 주 패널은 `background:#ffe9c9`.
 */
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
          className={`flex items-center justify-between border-b-[3px] border-ink px-3.5 py-[11px] ${
            tone === 'primary' ? 'bg-surface-raise' : ''
          }`}
        >
          <h2 className="text-xs font-bold tracking-[3px]">■ {title}</h2>
          {actions}
        </header>
      )}
      {children}
    </section>
  );
}
