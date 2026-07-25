import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppShell } from '../components/layout/AppShell';
import { Hero } from '../components/layout/Hero';
import { RightPanel } from '../components/layout/RightPanel';
import { ScreenPanel } from '../components/layout/ScreenPanel';

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <AppShell>
        <p>본문</p>
      </AppShell>
    </MemoryRouter>,
  );

describe('Hero', () => {
  it('아이브로우·제목·설명을 렌더한다', () => {
    render(<Hero eyebrow="▚▚ SIGNAL RECEIVED" title="유니버스 새 소식" description="설명" />);

    expect(screen.getByText('▚▚ SIGNAL RECEIVED')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: '유니버스 새 소식' })).toBeInTheDocument();
    expect(screen.getByText('설명')).toBeInTheDocument();
  });

  it('다크 그라디언트를 쓰고 높이를 지정할 수 있다', () => {
    const { container } = render(
      <Hero eyebrow="E" title="T" description="D" height={190} />,
    );

    expect(container.querySelector('[data-hero="true"]')).toHaveStyle({
      background: 'var(--gradient-dusk)',
      height: '190px',
    });
  });

  it('별·로켓·구름은 aria-hidden 장식이다', () => {
    const { container } = render(<Hero eyebrow="E" title="T" description="D" />);

    const decorations = container.querySelectorAll('[aria-hidden="true"]');
    expect(decorations.length).toBeGreaterThanOrEqual(3);
  });
});

describe('RightPanel', () => {
  it('정본 3개 패널을 순서대로 렌더한다', () => {
    render(
      <MemoryRouter>
        <RightPanel />
      </MemoryRouter>,
    );

    const headings = within(screen.getByRole('complementary', { name: '사이드 패널' }))
      .getAllByRole('heading', { level: 2 })
      .map((h) => h.textContent);

    expect(headings).toEqual(['■ 내 블로그', '■ 최근 알림', '■ 유니버스 현황']);
  });

  it('폭 300px에 sticky다', () => {
    render(
      <MemoryRouter>
        <RightPanel />
      </MemoryRouter>,
    );

    const panel = screen.getByRole('complementary', { name: '사이드 패널' });
    expect(panel).toHaveClass('w-[300px]');
    expect(panel).toHaveStyle({ position: 'sticky' });
  });
});

describe('ScreenPanel', () => {
  it('설정 메뉴 3항목을 렌더한다', () => {
    render(
      <MemoryRouter>
        <ScreenPanel kind="settings" />
      </MemoryRouter>,
    );

    const links = within(screen.getByRole('complementary', { name: '설정 메뉴' })).getAllByRole(
      'link',
    );

    expect(links.map((l) => ({ label: l.textContent, href: l.getAttribute('href') }))).toEqual([
      { label: '프로필', href: '/settings' },
      { label: '유니버스', href: '/settings/universe' },
      { label: '글·카테고리', href: '/settings/posts' },
    ]);
  });

  it('블로그 패널은 빈 상태를 렌더한다', () => {
    render(
      <MemoryRouter>
        <ScreenPanel kind="blog" />
      </MemoryRouter>,
    );

    expect(screen.getByRole('complementary', { name: '블로그 메뉴' })).toBeInTheDocument();
    expect(screen.getByText(/카테고리를 불러오면/)).toBeInTheDocument();
  });

  it('폭 240px로 앱 내비(210px)와 구분된다', () => {
    render(
      <MemoryRouter>
        <ScreenPanel kind="settings" />
      </MemoryRouter>,
    );

    expect(screen.getByRole('complementary', { name: '설정 메뉴' })).toHaveClass('w-60');
  });
});

describe('AppShell 영역 조합', () => {
  it('/ 는 히어로·앱 내비·우측 패널을 모두 렌더한다', () => {
    const { container } = renderAt('/');

    expect(container.querySelector('[data-hero="true"]')).not.toBeNull();
    expect(screen.getByRole('navigation', { name: '주 메뉴' })).toBeInTheDocument();
    expect(screen.getByRole('complementary', { name: '사이드 패널' })).toBeInTheDocument();
  });

  it('/settings 는 화면 전용 패널만 두고 앱 내비·우측 패널·히어로가 없다', () => {
    const { container } = renderAt('/settings');

    expect(screen.getByRole('complementary', { name: '설정 메뉴' })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: '주 메뉴' })).not.toBeInTheDocument();
    expect(screen.queryByRole('complementary', { name: '사이드 패널' })).toBeNull();
    expect(container.querySelector('[data-hero="true"]')).toBeNull();
  });

  it('/blog/:slug 는 블로그 패널을 렌더한다', () => {
    renderAt('/blog/zerostar');
    expect(screen.getByRole('complementary', { name: '블로그 메뉴' })).toBeInTheDocument();
  });

  it('단일 컬럼 화면에는 좌측 패널이 없다', () => {
    renderAt('/search');

    expect(screen.queryByRole('complementary', { name: '설정 메뉴' })).toBeNull();
    expect(screen.queryByRole('navigation', { name: '주 메뉴' })).not.toBeInTheDocument();
  });

  it('온보딩 화면에는 히어로를 따로 두지 않는다 (배경 자체가 다크다)', () => {
    const { container } = renderAt('/signin');

    expect(container.querySelector('[data-hero="true"]')).toBeNull();
    expect(container.querySelector('[data-layout="onboarding"]')).not.toBeNull();
  });
});
