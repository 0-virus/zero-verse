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
      className="border-[3px] border-ink bg-surface-warm"
    >
      {/* 헤더 밴드 — 정본은 아이브로우·제목·설명을 surface-raise 밴드에 넣고 3px로 본문과 가른다. */}
      <div className="border-b-[3px] border-ink bg-surface-raise px-8 py-6">
        <p className="font-pixel text-[10px] text-universe">{eyebrow}</p>
        <h1 className="mt-2 text-[21px] font-bold">{title}</h1>
        <p className="mt-1 text-[13px] text-text-body">{description}</p>
      </div>
      {/*
        children을 <p>로 감싸지 않는다. M0의 빈 상태 문구를 담으려고 넣은 <p>였는데,
        M2가 여기에 <form>을 넣으면서 `<p>` 안에 `<div>`·`<p>`·`<form>`이 들어가는
        잘못된 HTML이 됐다(React hydration 경고). 가운데 정렬도 placeholder용이라
        실제 폼에서는 라벨까지 가운데로 밀어 정본과 어긋났다.

        정본 본문에는 내부 테두리가 없다. M0가 빈 상태를 눈에 보이게 하려고 넣었던
        `border-2` 상자를 걷어낸다.
      */}
      <div className="px-8 py-6">
        {children ?? (
          <p className="text-center text-[13px] text-text-muted">아직 표시할 내용이 없습니다.</p>
        )}
      </div>
    </section>
  );
}
