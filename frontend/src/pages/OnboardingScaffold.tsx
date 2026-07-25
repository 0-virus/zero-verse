import type { ReactNode } from 'react';

/**
 * 온보딩 화면(`/signin`, `/signup`, `/blog/setup`) 골격.
 *
 * 다크 그라디언트 위에 놓이므로 카드 그림자는 `--shadow-on-dark`를 쓴다(DESIGN-SYSTEM §3).
 * 아이브로우는 Press Start 2P 영문 대문자 전용이다(§4).
 *
 * M0는 카드 경계와 타이포만 확정한다. 실제 폼과 인증 연동은 M1이다.
 */
export interface OnboardingScaffoldProps {
  eyebrow: string;
  title: string;
  description: string;
  children?: ReactNode;
}

export function OnboardingScaffold({
  eyebrow,
  title,
  description,
  children,
}: OnboardingScaffoldProps) {
  return (
    <section
      data-onboarding-scaffold="true"
      style={{ boxShadow: 'var(--shadow-on-dark)' }}
      className="border-[3px] border-ink bg-surface-warm px-8 py-9"
    >
      <p className="font-pixel text-[10px] tracking-[2px] text-universe">{eyebrow}</p>
      <h1 className="mt-3 text-[23px] font-bold">{title}</h1>
      <p className="mt-2 text-[13px] text-text-body">{description}</p>
      <div className="mt-6 border-2 border-shadow bg-surface px-5 py-10 text-center">
        <p className="text-[13px] text-text-muted">{children ?? '아직 표시할 내용이 없습니다.'}</p>
      </div>
    </section>
  );
}
