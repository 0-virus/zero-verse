import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppShell } from '../components/layout/AppShell';
import { resolveLayout } from '../lib/layout';

/**
 * 화면별 레이아웃 경계 검증 — DESIGN-SYSTEM §6.2 = PRD §6.6.
 *
 * M0의 책임은 경로별 컨테이너 폭·그리드·앱 셸 유무를 확정하는 것이다.
 */
describe('resolveLayout', () => {
  it.each([
    ['/', 1440, '210px 1fr 300px', true],
    ['/blog/my-blog', 1440, '240px 1fr', false],
    ['/blog/my-blog/42', 980, null, false],
    ['/write', 1060, null, false],
    ['/edit/42', 1060, null, false],
    ['/settings', 1240, '240px 1fr', false],
    ['/settings/universe', 1240, '240px 1fr', false],
    ['/settings/posts', 1240, '240px 1fr', false],
    ['/search', 1000, null, false],
    ['/notifications', 860, null, false],
    ['/admin', 1100, null, false],
    ['/admin/users/7', 860, null, false],
  ] as const)('%s → maxWidth %d, columns %s', (path, maxWidth, columns, appNav) => {
    const layout = resolveLayout(path);

    expect(layout.kind).toBe('app');
    expect(layout.maxWidth).toBe(maxWidth);
    expect(layout.columns).toBe(columns);
    expect(layout.appNav).toBe(appNav);
  });

  it.each([
    ['/signin', 420],
    ['/signup', 420],
    ['/blog/setup', 560],
  ] as const)('%s 은 온보딩 레이아웃이고 카드 폭 %dpx다', (path, cardWidth) => {
    const layout = resolveLayout(path);

    expect(layout.kind).toBe('onboarding');
    expect(layout.cardWidth).toBe(cardWidth);
    expect(layout.appNav).toBe(false);
  });

  it('미등록 `/settings-*` 경로는 설정 셸이 아니라 not-found 단일 컬럼이다', () => {
    const layout = resolveLayout('/settings-unknown');

    expect(layout.columns).toBeNull();
    expect(layout.screenPanel).toBeUndefined();
    expect(layout.maxWidth).toBe(860);
  });

  it('히어로는 `/`(240px)와 `/blog/:slug`(190px)에만 있다', () => {
    expect(resolveLayout('/').hero?.height).toBe(240);
    expect(resolveLayout('/blog/zerostar').hero?.height).toBe(190);
    expect(resolveLayout('/settings').hero).toBeUndefined();
    expect(resolveLayout('/search').hero).toBeUndefined();
    expect(resolveLayout('/signin').hero).toBeUndefined();
  });

  it('앱 내비 사이드바는 `/`에만 존재한다', () => {
    const withNav = [
      '/',
      '/blog/x',
      '/blog/x/1',
      '/write',
      '/edit/1',
      '/settings',
      '/settings/posts',
      '/search',
      '/notifications',
      '/admin',
      '/admin/users/1',
      '/signin',
      '/signup',
      '/blog/setup',
    ].filter((p) => resolveLayout(p).appNav);

    expect(withNav).toEqual(['/']);
  });
});

describe('AppShell 레이아웃 적용', () => {
  const renderAt = (path: string) =>
    render(
      <MemoryRouter initialEntries={[path]}>
        <AppShell>
          <p>본문</p>
        </AppShell>
      </MemoryRouter>,
    );

  it('온보딩 경로는 다크 그라디언트 컨테이너를 렌더하고 앱 컨테이너를 렌더하지 않는다', () => {
    const { container } = renderAt('/signin');

    const onboarding = container.querySelector('[data-layout="onboarding"]');
    expect(onboarding).not.toBeNull();
    expect(onboarding).toHaveStyle({ background: 'var(--gradient-auth)' });
    expect(container.querySelector('[data-layout="app"]')).toBeNull();
  });

  it('온보딩 중앙 카드 폭이 경로별로 다르다', () => {
    const signin = renderAt('/signin');
    expect(
      signin.container.querySelector('[data-onboarding-card="true"]'),
    ).toHaveStyle({ width: '420px' });

    const setup = renderAt('/blog/setup');
    expect(
      setup.container.querySelector('[data-onboarding-card="true"]'),
    ).toHaveStyle({ width: '560px' });
  });

  it('온보딩 경로에도 상단바는 남는다 (전 화면 공통)', () => {
    renderAt('/signup');
    expect(screen.getByRole('link', { name: 'ZEROVERSE' })).toBeInTheDocument();
  });

  it('/ 는 3열 그리드와 앱 내비를 렌더한다', () => {
    const { container } = renderAt('/');

    const app = container.querySelector('[data-layout="app"]');
    expect(app).toHaveStyle({
      maxWidth: '1440px',
      gridTemplateColumns: '210px 1fr 300px',
    });
    expect(screen.getByRole('navigation', { name: '주 메뉴' })).toBeInTheDocument();
  });

  it('단일 컬럼 화면은 grid가 아니라 block이다', () => {
    const { container } = renderAt('/notifications');

    const app = container.querySelector('[data-layout="app"]');
    expect(app).toHaveStyle({ display: 'block', maxWidth: '860px' });
    expect(screen.queryByRole('navigation', { name: '주 메뉴' })).not.toBeInTheDocument();
  });

  it('설정 화면은 240px 2열이며 앱 내비가 없다', () => {
    const { container } = renderAt('/settings');

    expect(container.querySelector('[data-layout="app"]')).toHaveStyle({
      maxWidth: '1240px',
      gridTemplateColumns: '240px 1fr',
    });
    expect(screen.queryByRole('navigation', { name: '주 메뉴' })).not.toBeInTheDocument();
  });
});
