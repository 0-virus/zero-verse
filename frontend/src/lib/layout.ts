/**
 * 화면별 레이아웃 상수 — 디자인 정본 `DESIGN-SYSTEM.md` §6.2 = PRD §6.6.
 *
 * M0의 책임은 **경로별 레이아웃 경계를 확정**하는 것이다. 각 화면의 실제 콘텐츠는 해당 마일스톤에서 채운다.
 */

export type LayoutKind =
  /** 상단바 아래 전폭 다크 그라디언트 + 중앙 카드. 앱 셸 없음. */
  | 'onboarding'
  /** 크림 페이퍼 앱 레이아웃. */
  | 'app';

export interface LayoutSpec {
  kind: LayoutKind;
  /** 컨테이너 max-width(px) */
  maxWidth: number;
  /** 컨테이너 padding CSS */
  padding: string;
  /** grid-template-columns. 단일 컬럼이면 null */
  columns: string | null;
  /** 앱 내비 사이드바(210px) 노출 여부 — `/`에만 true */
  appNav: boolean;
  /** 온보딩 중앙 카드 폭(px) */
  cardWidth?: number;
  /** 상단 다크 히어로. 없으면 undefined */
  hero?: { height: number; eyebrow: string; title: string; description: string };
  /** 우측 패널(300px) 노출 여부 — `/`에만 true(PRD §6.7) */
  rightPanel?: boolean;
  /** 화면 전용 좌측 패널(240px). 앱 내비가 아니다(PRD §6.7) */
  screenPanel?: 'settings' | 'blog';
}

const APP_PADDING_WIDE = '24px 40px 60px';
const APP_PADDING_NARROW = '28px 40px 70px';

/** 라우트 패턴 → 레이아웃. 배열 순서대로 먼저 매칭되는 것을 쓴다. */
const RULES: Array<{ test: (path: string) => boolean; spec: LayoutSpec }> = [
  {
    test: (p) => p === '/signin' || p === '/signup',
    spec: {
      kind: 'onboarding',
      maxWidth: 1440,
      padding: '0',
      columns: null,
      appNav: false,
      cardWidth: 420,
    },
  },
  {
    test: (p) => p === '/blog/setup',
    spec: {
      kind: 'onboarding',
      maxWidth: 1440,
      padding: '0',
      columns: null,
      appNav: false,
      cardWidth: 560,
    },
  },
  {
    test: (p) => p === '/',
    spec: {
      kind: 'app',
      maxWidth: 1440,
      padding: APP_PADDING_WIDE,
      columns: '210px 1fr 300px',
      appNav: true,
      rightPanel: true,
      hero: {
        height: 240,
        eyebrow: '▚▚ SIGNAL RECEIVED',
        title: '유니버스 새 소식',
        description: '내가 발견한 우주에서 도착한 최신 신호를 확인하세요',
      },
    },
  },
  {
    // /blog/:slug/:postId — 단일 컬럼 980
    test: (p) => /^\/blog\/[^/]+\/[^/]+$/.test(p),
    spec: { kind: 'app', maxWidth: 980, padding: APP_PADDING_NARROW, columns: null, appNav: false },
  },
  {
    // /blog/:slug — 240px 화면 전용 패널 + 본문
    test: (p) => /^\/blog\/[^/]+$/.test(p),
    spec: {
      kind: 'app',
      maxWidth: 1440,
      padding: APP_PADDING_WIDE,
      columns: '240px 1fr',
      appNav: false,
      screenPanel: 'blog',
      hero: {
        height: 190,
        eyebrow: 'MY UNIVERSE / BLOG',
        title: '블로그',
        description: '블로그 정보를 불러오면 여기에 표시됩니다.',
      },
    },
  },
  {
    test: (p) => p === '/write' || /^\/edit\/[^/]+$/.test(p),
    spec: { kind: 'app', maxWidth: 1060, padding: APP_PADDING_NARROW, columns: null, appNav: false },
  },
  {
    // 등록된 설정 경로만. `/settings-unknown` 같은 미등록 경로는 not-found 단일 컬럼으로 떨어진다.
    test: (p) => ['/settings', '/settings/universe', '/settings/posts'].includes(p),
    spec: {
      kind: 'app',
      maxWidth: 1240,
      padding: APP_PADDING_NARROW,
      columns: '240px 1fr',
      appNav: false,
      screenPanel: 'settings',
    },
  },
  {
    test: (p) => p === '/search',
    spec: { kind: 'app', maxWidth: 1000, padding: APP_PADDING_NARROW, columns: null, appNav: false },
  },
  {
    test: (p) => p === '/notifications',
    spec: { kind: 'app', maxWidth: 860, padding: APP_PADDING_NARROW, columns: null, appNav: false },
  },
  {
    test: (p) => /^\/admin\/users\/[^/]+$/.test(p),
    spec: { kind: 'app', maxWidth: 860, padding: APP_PADDING_NARROW, columns: null, appNav: false },
  },
  {
    test: (p) => p === '/admin',
    spec: { kind: 'app', maxWidth: 1100, padding: APP_PADDING_NARROW, columns: null, appNav: false },
  },
];

/** 매칭되지 않는 경로(not-found 등)의 기본값. */
const FALLBACK: LayoutSpec = {
  kind: 'app',
  maxWidth: 860,
  padding: APP_PADDING_NARROW,
  columns: null,
  appNav: false,
};

export function resolveLayout(pathname: string): LayoutSpec {
  return RULES.find((rule) => rule.test(pathname))?.spec ?? FALLBACK;
}
