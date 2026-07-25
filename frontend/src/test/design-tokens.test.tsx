import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppShell } from '../components/layout/AppShell';
import { Hero, HeroAvatar } from '../components/layout/Hero';
import { Panel } from '../components/ui/Panel';
import { RightPanel } from '../components/layout/RightPanel';
import { TopBar } from '../components/layout/TopBar';
import { ScreenPanel } from '../components/layout/ScreenPanel';
import {
  AUTH_STARS,
  BLOG_STARS,
  HERO_STARS_PRIMARY,
  HERO_STARS_SECONDARY,
  SETUP_STARS,
} from '../components/layout/StarField';

/**
 * 정본 수치 회귀 방지.
 *
 * 리뷰에서 반복 지적된 값들(간격·패딩·별 좌표·로켓 위치)을 테스트로 고정해,
 * 이후 마일스톤에서 무심코 바뀌면 실패하도록 한다.
 */
const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <AppShell>
        <p>본문</p>
      </AppShell>
    </MemoryRouter>,
  );

/**
 * 정본 `.dc.html` 인라인 style에서 그대로 옮긴 **독립 기대값**.
 * 구현 상수와 비교해 좌표가 바뀌면 실패한다(개수만 세는 검사로는 못 잡는다).
 */
const EXPECTED = {
  heroPrimary:
    '110px 24px 0 #ffd9a0,250px 8px 0 #fff,400px 30px 0 #ffe9c9,540px 14px 0 #fff,' +
    '690px 34px 0 #ffd9a0,830px 10px 0 #fff,980px 26px 0 #ffe9c9,1120px 18px 0 #fff,' +
    '1260px 36px 0 #ffd9a0,60px 48px 0 #fff,470px 52px 0 #ffd9a0,900px 54px 0 #fff',
  heroSecondary:
    '160px -14px 0 #fff,340px 12px 0 #ffe9c9,560px -20px 0 #fff,' +
    '760px 6px 0 #ffe9c9,1000px -10px 0 #fff,1180px 14px 0 #ffe9c9',
  blog:
    '130px 20px 0 #ffd9a0,290px 6px 0 #fff,450px 34px 0 #ffe9c9,610px 12px 0 #fff,' +
    '780px 40px 0 #ffd9a0,930px 8px 0 #fff,1090px 28px 0 #ffe9c9,1230px 16px 0 #fff,' +
    '70px 52px 0 #ffd9a0,520px 58px 0 #fff,990px 60px 0 #ffd9a0',
  setup:
    '170px 40px 0 #ffd9a0,350px 10px 0 #fff,540px 70px 0 #ffe9c9,720px 24px 0 #fff,' +
    '900px 90px 0 #ffd9a0,1080px 30px 0 #fff,1230px 60px 0 #ffe9c9,90px 140px 0 #fff,' +
    '420px 180px 0 #ffd9a0,820px 170px 0 #fff,1150px 200px 0 #ffd9a0',
  auth:
    '170px 40px 0 #ffd9a0,350px 10px 0 #fff,540px 70px 0 #ffe9c9,720px 24px 0 #fff,' +
    '900px 90px 0 #ffd9a0,1080px 30px 0 #fff,1230px 60px 0 #ffe9c9,90px 140px 0 #fff,' +
    '420px 180px 0 #ffd9a0,820px 170px 0 #fff,1150px 200px 0 #ffd9a0,260px 260px 0 #fff,' +
    '660px 300px 0 #ffe9c9,1010px 280px 0 #fff',
} as const;

/** 정본 로켓 8단 — 위→아래 폭/색. 각 단 높이는 7px. */
const EXPECTED_ROCKET: Array<[number, string]> = [
  [36, 'rgb(255, 217, 160)'],
  [58, 'rgb(255, 192, 116)'],
  [72, 'rgb(255, 157, 108)'],
  [80, 'rgb(232, 93, 117)'],
  [80, 'rgb(200, 107, 177)'],
  [72, 'rgb(139, 74, 158)'],
  [58, 'rgb(92, 58, 130)'],
  [36, 'rgb(75, 42, 123)'],
];

describe('별 좌표 (정본 .dc.html 인라인 style과 문자열 일치)', () => {
  it.each([
    ['HERO_STARS_PRIMARY', HERO_STARS_PRIMARY, EXPECTED.heroPrimary],
    ['HERO_STARS_SECONDARY', HERO_STARS_SECONDARY, EXPECTED.heroSecondary],
    ['BLOG_STARS', BLOG_STARS, EXPECTED.blog],
    ['SETUP_STARS', SETUP_STARS, EXPECTED.setup],
    ['AUTH_STARS', AUTH_STARS, EXPECTED.auth],
  ])('%s 는 정본 좌표와 정확히 일치한다', (_name, actual, expected) => {
    expect(actual).toBe(expected);
  });

  it('화면마다 다른 별 배치를 쓴다', () => {
    const all = [HERO_STARS_PRIMARY, HERO_STARS_SECONDARY, BLOG_STARS, AUTH_STARS];
    expect(new Set(all).size).toBe(all.length);
  });
});

describe('픽셀 로켓 8단', () => {
  it('폭·색·높이가 정본과 일치한다', () => {
    const { container } = render(<Hero variant="feed" eyebrow="E" title="T" description="D" />);
    const stages = [...(container.querySelector('[data-pixel-rocket="true"]')?.children ?? [])];

    expect(stages).toHaveLength(8);
    stages.forEach((el, i) => {
      const [width, background] = EXPECTED_ROCKET[i];
      expect(el).toHaveStyle({ width: `${width}px`, height: '7px', background });
    });
  });

  it('4번째 단(동체)에만 날개 box-shadow가 있다', () => {
    const { container } = render(<Hero variant="feed" eyebrow="E" title="T" description="D" />);
    const stages = [...(container.querySelector('[data-pixel-rocket="true"]')?.children ?? [])];

    const withWings = stages.filter((el) =>
      (el as HTMLElement).style.boxShadow.includes('#fff1d6'),
    );
    expect(withWings).toHaveLength(1);
    expect(stages.indexOf(withWings[0])).toBe(3);
  });
});

describe('블로그 히어로 수치', () => {
  const renderBlog = () =>
    render(
      <Hero
        variant="blog"
        eyebrow="MY UNIVERSE / BLOG"
        title="블로그"
        description="설명"
        avatar={<HeroAvatar />}
      />,
    );

  it('콘텐츠는 하단 정렬 · padding 0 60px 24px · gap 18px', () => {
    const { container } = renderBlog();
    const content = container.querySelector('[data-hero-actions="true"]')?.parentElement;

    expect(content).toHaveStyle({ padding: '0px 60px 24px', gap: '18px' });
    expect(content?.className).toContain('items-end');
  });

  it('eyebrow 9px/tracking 2px · h1 28px/tracking 2px', () => {
    renderBlog();

    const eyebrow = screen.getByText('MY UNIVERSE / BLOG');
    expect(eyebrow.className).toContain('text-[9px]');
    expect(eyebrow.className).toContain('tracking-[2px]');

    const h1 = screen.getByRole('heading', { level: 1, name: '블로그' });
    expect(h1.className).toContain('text-[28px]');
    expect(h1.className).toContain('tracking-[2px]');
  });

  it('HeroAvatar는 76px · 3px 잉크 보더 · 다크 그림자', () => {
    renderBlog();

    const avatar = screen.getByRole('img', { name: '블로그 아바타' });
    expect(avatar).toHaveStyle({
      width: '76px',
      height: '76px',
      boxShadow: '5px 5px 0 rgba(43,27,61,.5)',
    });
    expect(avatar.className).toContain('border-[3px]');
    expect(avatar.className).toContain('border-ink');
  });
});

describe('Hero variant', () => {
  it('feed는 별 2레이어 + 로켓 right:150/top:16', () => {
    const { container } = render(<Hero variant="feed" eyebrow="E" title="T" description="D" />);

    expect(container.querySelectorAll('[data-pixel-stars="true"]')).toHaveLength(2);
    expect(container.querySelector('[data-pixel-rocket="true"]')).toHaveStyle({
      right: '150px',
      top: '16px',
    });
    expect(container.querySelector('[data-hero="true"]')).toHaveStyle({ height: '240px' });
  });

  it('blog는 별 1레이어 + 로켓 right:170/top:34 + 하단 정렬', () => {
    const { container } = render(<Hero variant="blog" eyebrow="E" title="T" description="D" />);

    expect(container.querySelectorAll('[data-pixel-stars="true"]')).toHaveLength(1);
    expect(container.querySelector('[data-pixel-rocket="true"]')).toHaveStyle({
      right: '170px',
      top: '34px',
    });
    expect(container.querySelector('[data-hero="true"]')).toHaveStyle({ height: '190px' });
    expect(container.querySelector('[data-hero-actions="true"]')).not.toBeNull();
  });

  it('blog는 아바타·액션 슬롯을 받는다', () => {
    render(
      <Hero
        variant="blog"
        eyebrow="E"
        title="T"
        description="D"
        avatar={<span data-testid="avatar" />}
        actions={<button type="button">RSS</button>}
      />,
    );

    expect(screen.getByTestId('avatar')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'RSS' })).toBeInTheDocument();
  });

  it('두 variant 모두 구름을 렌더한다 (PRD §9.4-Z 사용자 결정)', () => {
    for (const variant of ['feed', 'blog'] as const) {
      const { container } = render(
        <Hero variant={variant} eyebrow="E" title="T" description="D" />,
      );
      expect(container.querySelector('[data-hero-clouds="true"]')).not.toBeNull();
    }
  });
});

describe('온보딩 장식 — 경로별로 다르다', () => {
  it('/signin·/signup 은 별 + 로켓', () => {
    for (const path of ['/signin', '/signup']) {
      const { container } = renderAt(path);
      expect(container.querySelectorAll('[data-pixel-stars="true"]')).toHaveLength(1);
      expect(container.querySelectorAll('[data-pixel-rocket="true"]')).toHaveLength(1);
    }
  });

  it('/blog/setup 은 별만 있고 **로켓이 없다** (정본 SCREEN: BLOG SETUP)', () => {
    const { container } = renderAt('/blog/setup');

    expect(container.querySelectorAll('[data-pixel-stars="true"]')).toHaveLength(1);
    expect(container.querySelectorAll('[data-pixel-rocket="true"]')).toHaveLength(0);
  });
});

describe('정본 수치 고정', () => {
  it('Panel 헤더는 11px 14px 패딩 · 12px · tracking 3px', () => {
    render(<Panel title="내 블로그">본문</Panel>);

    const heading = screen.getByRole('heading', { level: 2, name: '■ 내 블로그' });
    const header = heading.parentElement;
    expect(header?.className).toContain('px-3.5');
    expect(header?.className).toContain('py-[11px]');
    expect(heading.className).toContain('text-xs');
    expect(heading.className).toContain('tracking-[3px]');
  });

  it('RightPanel 간격은 18px', () => {
    render(
      <MemoryRouter>
        <RightPanel />
      </MemoryRouter>,
    );

    expect(screen.getByRole('complementary', { name: '사이드 패널' })).toHaveStyle({ gap: '18px' });
  });

  it('블로그 ScreenPanel 간격도 18px', () => {
    render(
      <MemoryRouter>
        <ScreenPanel kind="blog" />
      </MemoryRouter>,
    );

    expect(screen.getByRole('complementary', { name: '블로그 메뉴' })).toHaveStyle({ gap: '18px' });
  });

  it('TopBar는 height 64 · padding 0 28px · gap 20 · 검색바 520px', () => {
    const { container } = render(
      <MemoryRouter>
        <TopBar />
      </MemoryRouter>,
    );

    const header = container.querySelector('header');
    expect(header).toHaveStyle({ height: '64px', padding: '0 28px', gap: '20px' });
    expect(screen.getByRole('search')).toHaveStyle({ width: '520px' });
  });
});
