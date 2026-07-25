/**
 * 우주(다크) 히어로 스트립.
 *
 * 값은 `ZeroVerse Main Feed v2.dc.html`의 HERO STRIP 인라인 style이 정본이다
 * (DESIGN-SYSTEM §2.5·§5, PRD §6.6).
 *
 * 그라디언트·픽셀 별·픽셀 로켓은 **다크 영역에만** 쓴다. 별 반짝임은 페이드가 아니라
 * `steps(2)`로 픽셀처럼 끊긴다.
 *
 * `/` 메인 피드는 240px, `/blog/:slug`는 190px(DESIGN-SYSTEM §6.2).
 */
export interface HeroProps {
  /** Press Start 2P 영문 대문자 전용 — 한글 금지 */
  eyebrow: string;
  title: string;
  description: string;
  height?: number;
}

/** 픽셀 로켓 8단 — 위→아래 폭 36/58/72/80/80/72/58/36px, 각 7px(§2.5). */
const ROCKET = [
  { w: 36, bg: '#ffd9a0' },
  { w: 58, bg: '#ffc074' },
  { w: 72, bg: '#ff9d6c' },
  { w: 80, bg: '#e85d75', shadow: '-28px 0 0 -2px #fff1d6, 28px 0 0 -2px #fff1d6' },
  { w: 80, bg: '#c86bb1' },
  { w: 72, bg: '#8b4a9e' },
  { w: 58, bg: '#5c3a82' },
  { w: 36, bg: '#4b2a7b' },
];

const STARS_PRIMARY =
  '110px 24px 0 #ffd9a0,250px 8px 0 #fff,400px 30px 0 #ffe9c9,540px 14px 0 #fff,' +
  '690px 34px 0 #ffd9a0,830px 10px 0 #fff,980px 26px 0 #ffe9c9,1120px 18px 0 #fff,' +
  '1260px 36px 0 #ffd9a0,60px 48px 0 #fff,470px 52px 0 #ffd9a0,900px 54px 0 #fff';

const STARS_SECONDARY =
  '160px -14px 0 #fff,340px 12px 0 #ffe9c9,560px -20px 0 #fff,' +
  '760px 6px 0 #ffe9c9,1000px -10px 0 #fff,1180px 14px 0 #ffe9c9';

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
      <div
        aria-hidden="true"
        style={{
          position: 'absolute',
          width: 3,
          height: 3,
          background: '#fff',
          top: 20,
          left: 120,
          boxShadow: STARS_PRIMARY,
          animation: 'tw 2.6s steps(2) infinite',
        }}
      />
      <div
        aria-hidden="true"
        style={{
          position: 'absolute',
          width: 2,
          height: 2,
          background: '#ffe9c9',
          top: 44,
          left: 180,
          boxShadow: STARS_SECONDARY,
          animation: 'tw 3.4s steps(2) infinite',
        }}
      />

      <div
        aria-hidden="true"
        style={{ position: 'absolute', right: 150, top: 16, animation: 'floaty 5s ease-in-out infinite' }}
        className="flex flex-col items-center"
      >
        {ROCKET.map((stage, i) => (
          <div
            key={i}
            style={{ width: stage.w, height: 7, background: stage.bg, boxShadow: stage.shadow }}
          />
        ))}
      </div>

      <div
        aria-hidden="true"
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
