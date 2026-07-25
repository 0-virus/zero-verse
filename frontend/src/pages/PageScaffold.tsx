import type { ReactNode } from 'react';

/**
 * M0 페이지 골격.
 *
 * 각 라우트가 제목·레이아웃·빈 상태만 렌더한다. 샘플 사용자나 가짜 API 데이터를 넣지 않는다.
 * 실제 데이터 연동은 해당 마일스톤(M1~M9)에서 구현한다.
 */
export interface PageScaffoldProps {
  /** Press Start 2P 아이브로우 — 영문 대문자만(한글 금지). */
  eyebrow: string;
  title: string;
  description: string;
  children?: ReactNode;
}

export function PageScaffold({ eyebrow, title, description, children }: PageScaffoldProps) {
  return (
    <section>
      <p className="font-pixel text-[10px] tracking-[2px] text-universe">{eyebrow}</p>
      <h1 className="mt-3 text-[23px] font-bold">{title}</h1>
      <p className="mt-2 text-[13px] text-text-body">{description}</p>
      <div className="mt-6 border-[3px] border-ink bg-surface px-6 py-12 text-center shadow-card">
        <p className="text-[13px] text-text-muted">{children ?? '아직 표시할 내용이 없습니다.'}</p>
      </div>
    </section>
  );
}
