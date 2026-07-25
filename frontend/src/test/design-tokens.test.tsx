import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppShell } from '../components/layout/AppShell';
import { Hero } from '../components/layout/Hero';
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

describe('별 좌표 (정본 .dc.html 인라인 style)', () => {
  it('메인 히어로는 1차 12개 · 2차 6개 별을 쓴다', () => {
    expect(HERO_STARS_PRIMARY.split(',')).toHaveLength(12);
    expect(HERO_STARS_SECONDARY.split(',')).toHaveLength(6);
  });

  it('블로그 히어로는 전용 11개 별을 쓰며 메인과 다르다', () => {
    expect(BLOG_STARS.split(',')).toHaveLength(11);
    expect(BLOG_STARS).not.toBe(HERO_STARS_PRIMARY);
  });

  it('초기 설정 별은 로그인 14개의 앞 11개 축약본이다', () => {
    expect(AUTH_STARS.split(',')).toHaveLength(14);
    expect(SETUP_STARS.split(',')).toHaveLength(11);
    expect(AUTH_STARS.startsWith(SETUP_STARS)).toBe(true);
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
