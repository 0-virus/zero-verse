import type { ReactNode } from 'react';
import {
  BLOG_STARS,
  HERO_STARS_PRIMARY,
  HERO_STARS_SECONDARY,
  PixelRocket,
  PixelStars,
} from './StarField';

/**
 * 우주(다크) 히어로 스트립.
 *
 * 화면마다 별 좌표·로켓 위치·콘텐츠 정렬이 다르므로 variant로 나눈다.
 *
 * - `feed` — `Main Feed v2.dc.html` HERO STRIP. 240px, 별 2레이어, 로켓 `right:150/top:16`,
 *   콘텐츠 세로 중앙.
 * - `blog` — `Pages.dc.html` SCREEN: BLOG HOME. 190px, 전용 별 11개, 로켓 `right:170/top:34`,
 *   콘텐츠 **하단 정렬** + 76px 아바타 + 우측 액션 슬롯.
 *
 * **구름 스트립**: 정본 템플릿의 `showClouds` 기본값은 `false`이지만 ZeroVerse는 **항상 켠다**
 * — 사용자 결정(2026-07-25, PRD §9.4-Z).
 *
 * M0는 구조와 슬롯까지 확정한다. 아바타 이미지·블로그명·소개·액션 버튼의 실제 값은 M2(블로그 설정),
 * 유니버스 신청 액션은 M5다.
 */
export type HeroVariant = 'feed' | 'blog';

export interface HeroProps {
  variant?: HeroVariant;
  /** Press Start 2P 영문 대문자 전용 — 한글 금지 */
  eyebrow: string;
  title: string;
  description: string;
  height?: number;
  /** `blog` variant의 76px 아바타 슬롯 */
  avatar?: ReactNode;
  /** `blog` variant의 우측 액션 슬롯(유니버스 신청·RSS 등) */
  actions?: ReactNode;
}

const CLOUD_CLIP =
  'polygon(0 60%,6% 60%,6% 30%,14% 30%,14% 55%,24% 55%,24% 20%,34% 20%,34% 50%,' +
  '46% 50%,46% 35%,58% 35%,58% 60%,70% 60%,70% 25%,82% 25%,82% 45%,92% 45%,92% 60%,100% 60%,100% 100%,0 100%)';

export function Hero({
  variant = 'feed',
  eyebrow,
  title,
  description,
  height,
  avatar,
  actions,
}: HeroProps) {
  const isBlog = variant === 'blog';
  const resolvedHeight = height ?? (isBlog ? 190 : 240);

  return (
    <div
      data-hero="true"
      data-hero-variant={variant}
      style={{ height: resolvedHeight, background: 'var(--gradient-dusk)' }}
      className="relative overflow-hidden border-b-[3px] border-ink"
    >
      {isBlog ? (
        <>
          <PixelStars top={22} left={140} size={3} color="#fff" shadow={BLOG_STARS} duration={2.6} />
          <PixelRocket duration={5} style={{ right: 170, top: 34 }} />
        </>
      ) : (
        <>
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
        </>
      )}

      <div
        aria-hidden="true"
        data-hero-clouds="true"
        style={{ background: 'var(--color-paper)', clipPath: CLOUD_CLIP }}
        className="absolute inset-x-0 bottom-0 h-[34px]"
      />

      {isBlog ? (
        <div
          style={{ padding: '0 60px 24px', gap: 18 }}
          className="relative z-[2] flex h-full items-end"
        >
          {avatar}
          <div>
            <p className="mb-2 font-pixel text-[9px] tracking-[2px] text-text-on-dark">{eyebrow}</p>
            <h1
              style={{ textShadow: '3px 3px 0 #4b2a7b' }}
              className="mb-1.5 text-[28px] font-bold tracking-[2px] text-white"
            >
              {title}
            </h1>
            <p className="text-[13px] text-text-on-dark">{description}</p>
          </div>
          <div className="flex-1" />
          <div data-hero-actions="true" className="flex gap-2.5">
            {actions}
          </div>
        </div>
      ) : (
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
      )}
    </div>
  );
}

/** 블로그 히어로의 76px 아바타 슬롯(정본: 흰 배경 + 3px 잉크 보더 + 다크 그림자). */
export function HeroAvatar({ emoji = '🪐' }: { emoji?: string }) {
  return (
    <span
      role="img"
      aria-label="블로그 아바타"
      style={{ width: 76, height: 76, boxShadow: '5px 5px 0 rgba(43,27,61,.5)', fontSize: 34 }}
      className="grid shrink-0 place-items-center border-[3px] border-ink bg-surface"
    >
      {emoji}
    </span>
  );
}
