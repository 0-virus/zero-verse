import {
  HERO_STARS_PRIMARY,
  HERO_STARS_SECONDARY,
  PixelRocket,
  PixelStars,
} from './StarField';

/**
 * 우주(다크) 히어로 스트립.
 *
 * 값은 `ZeroVerse Main Feed v2.dc.html`의 HERO STRIP 인라인 style이 정본이다
 * (DESIGN-SYSTEM §2.5·§5, PRD §6.6).
 *
 * `/` 메인 피드는 240px, `/blog/:slug`는 190px(DESIGN-SYSTEM §6.2).
 *
 * **구름 스트립**: 정본 템플릿의 `showClouds` 기본값은 `false`이지만, ZeroVerse는 **항상 켠다**
 * — 사용자 결정(2026-07-25, PRD §9.4-Z). 픽셀 스카이라인이 다크 히어로와 크림 페이퍼 본문 사이의
 * 전환을 담당한다.
 */
export interface HeroProps {
  /** Press Start 2P 영문 대문자 전용 — 한글 금지 */
  eyebrow: string;
  title: string;
  description: string;
  height?: number;
}

const CLOUD_CLIP =
  'polygon(0 60%,6% 60%,6% 30%,14% 30%,14% 55%,24% 55%,24% 20%,34% 20%,34% 50%,' +
  '46% 50%,46% 35%,58% 35%,58% 60%,70% 60%,70% 25%,82% 25%,82% 45%,92% 45%,92% 60%,100% 60%,100% 100%,0 100%)';

export function Hero({ eyebrow, title, description, height = 240 }: HeroProps) {
  return (
    <div
      data-hero="true"
      style={{ height, background: 'var(--gradient-dusk)' }}
      className="relative overflow-hidden border-b-[3px] border-ink"
    >
      <PixelStars
        top={20}
        left={120}
        size={3}
        color="#fff"
        shadow={HERO_STARS_PRIMARY}
        duration={2.6}
      />
      <PixelStars
        top={44}
        left={180}
        size={2}
        color="#ffe9c9"
        shadow={HERO_STARS_SECONDARY}
        duration={3.4}
      />
      <PixelRocket duration={5} style={{ right: 150, top: 16 }} />

      <div
        aria-hidden="true"
        data-hero-clouds="true"
        style={{ background: 'var(--color-paper)', clipPath: CLOUD_CLIP }}
        className="absolute inset-x-0 bottom-0 h-[34px]"
      />

      <div
        style={{ padding: '0 60px', gap: 8 }}
        className="relative z-[2] flex h-full flex-col justify-center"
      >
        <p className="font-pixel text-[10px] tracking-[3px] text-text-on-dark">{eyebrow}</p>
        <h1
          style={{ textShadow: '3px 3px 0 #4b2a7b' }}
          className="text-[30px] font-bold tracking-[3px] text-white"
        >
          {title}
        </h1>
        <p className="text-[13px] text-text-on-dark">{description}</p>
      </div>
    </div>
  );
}
