/**
 * 다크 영역 장식 — 픽셀 별과 픽셀 로켓.
 *
 * 값은 정본 인라인 style이 출처다(DESIGN-SYSTEM §2.5·§5).
 * 별 반짝임은 페이드가 아니라 `steps(2)`로 픽셀처럼 끊긴다.
 */

/** 픽셀 로켓 8단 — 위→아래 폭 36/58/72/80/80/72/58/36px, 각 7px. */
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

export interface PixelStarsProps {
  /** 기준점 좌표와 크기 */
  top: number;
  left: number;
  size: number;
  color: string;
  /** 다중 box-shadow로 흩뿌린 별들 */
  shadow: string;
  /** 반짝임 주기(초) */
  duration: number;
}

export function PixelStars({ top, left, size, color, shadow, duration }: PixelStarsProps) {
  return (
    <div
      aria-hidden="true"
      data-pixel-stars="true"
      style={{
        position: 'absolute',
        width: size,
        height: size,
        background: color,
        top,
        left,
        boxShadow: shadow,
        animation: `tw ${duration}s steps(2) infinite`,
      }}
    />
  );
}

export interface PixelRocketProps {
  /** 부유 애니메이션 주기(초). 히어로 5s, 로그인 6s. */
  duration: number;
  style?: React.CSSProperties;
}

export function PixelRocket({ duration, style }: PixelRocketProps) {
  return (
    <div
      aria-hidden="true"
      data-pixel-rocket="true"
      style={{ position: 'absolute', animation: `floaty ${duration}s ease-in-out infinite`, ...style }}
      className="flex flex-col items-center"
    >
      {ROCKET.map((stage, i) => (
        <div
          key={i}
          style={{ width: stage.w, height: 7, background: stage.bg, boxShadow: stage.shadow }}
        />
      ))}
    </div>
  );
}

/** `Main Feed v2.dc.html` HERO STRIP 별 좌표. */
export const HERO_STARS_PRIMARY =
  '110px 24px 0 #ffd9a0,250px 8px 0 #fff,400px 30px 0 #ffe9c9,540px 14px 0 #fff,' +
  '690px 34px 0 #ffd9a0,830px 10px 0 #fff,980px 26px 0 #ffe9c9,1120px 18px 0 #fff,' +
  '1260px 36px 0 #ffd9a0,60px 48px 0 #fff,470px 52px 0 #ffd9a0,900px 54px 0 #fff';

export const HERO_STARS_SECONDARY =
  '160px -14px 0 #fff,340px 12px 0 #ffe9c9,560px -20px 0 #fff,' +
  '760px 6px 0 #ffe9c9,1000px -10px 0 #fff,1180px 14px 0 #ffe9c9';

/** `Pages.dc.html` SCREEN: BLOG HOME 별 좌표(11개). 메인 피드와 다른 배치다. */
export const BLOG_STARS =
  '130px 20px 0 #ffd9a0,290px 6px 0 #fff,450px 34px 0 #ffe9c9,610px 12px 0 #fff,' +
  '780px 40px 0 #ffd9a0,930px 8px 0 #fff,1090px 28px 0 #ffe9c9,1230px 16px 0 #fff,' +
  '70px 52px 0 #ffd9a0,520px 58px 0 #fff,990px 60px 0 #ffd9a0';

/** `Pages.dc.html` SCREEN: BLOG SETUP 별 좌표(11개). LOGIN에서 마지막 3개가 빠진 축약본이며 **로켓이 없다**. */
export const SETUP_STARS =
  '170px 40px 0 #ffd9a0,350px 10px 0 #fff,540px 70px 0 #ffe9c9,720px 24px 0 #fff,' +
  '900px 90px 0 #ffd9a0,1080px 30px 0 #fff,1230px 60px 0 #ffe9c9,90px 140px 0 #fff,' +
  '420px 180px 0 #ffd9a0,820px 170px 0 #fff,1150px 200px 0 #ffd9a0';

/** `Pages.dc.html` SCREEN: LOGIN 별 좌표(14개). 히어로보다 넓게 흩뿌려진다. */
export const AUTH_STARS =
  '170px 40px 0 #ffd9a0,350px 10px 0 #fff,540px 70px 0 #ffe9c9,720px 24px 0 #fff,' +
  '900px 90px 0 #ffd9a0,1080px 30px 0 #fff,1230px 60px 0 #ffe9c9,90px 140px 0 #fff,' +
  '420px 180px 0 #ffd9a0,820px 170px 0 #fff,1150px 200px 0 #ffd9a0,260px 260px 0 #fff,' +
  '660px 300px 0 #ffe9c9,1010px 280px 0 #fff';
