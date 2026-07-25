import type { ReactNode } from 'react';

/** 디자인 정본 §7.10. 게시글 본문 영역. */
export function Prose({ children }: { children: ReactNode }) {
  return (
    <div className="flex flex-col gap-[18px] px-[34px] py-[30px] text-[15px] leading-[1.85] text-text-prose">
      {children}
    </div>
  );
}

/** 인용·콜아웃. 접두 ★. */
export function Callout({ children }: { children: ReactNode }) {
  return (
    <blockquote className="border-l-[6px] border-accent bg-surface-soft px-[18px] py-3.5 text-sm text-text-body">
      ★ {children}
    </blockquote>
  );
}

/** 코드 블록. */
export function CodeBlock({ code }: { code: string }) {
  return (
    <pre className="border-2 border-ink bg-ink px-[18px] py-4 font-mono text-[13px] leading-[1.7] text-text-on-ink shadow-btn">
      <code>{code}</code>
    </pre>
  );
}
