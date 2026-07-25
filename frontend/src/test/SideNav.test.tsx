import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { SideNav } from '../components/layout/SideNav';

/** 디자인 정본 구조 검증 — `ZeroVerse Main Feed v2.dc.html` LEFT NAV, PRD §6.7. */
describe('SideNav', () => {
  const setup = (path = '/') =>
    render(
      <MemoryRouter initialEntries={[path]}>
        <SideNav />
      </MemoryRouter>,
    );

  const nav = () => screen.getByRole('navigation', { name: '주 메뉴' });

  it('NAVIGATION 헤더를 렌더한다', () => {
    setup();
    expect(screen.getByRole('heading', { level: 2, name: 'NAVIGATION' })).toBeInTheDocument();
  });

  it('정본이 정한 5개 항목을 순서와 링크까지 일치시킨다', () => {
    setup();

    const links = within(nav()).getAllByRole('link');
    const actual = links.map((link) => ({
      label: link.textContent?.replace(/[^A-Za-z ]/g, '').trim(),
      href: link.getAttribute('href'),
    }));

    expect(actual).toEqual([
      { label: 'Home', href: '/' },
      { label: 'My Blog', href: '/blog/me' },
      { label: 'Search', href: '/search' },
      { label: 'Universe', href: '/settings/universe' },
      { label: 'Settings', href: '/settings' },
    ]);
  });

  it.each([
    ['▲', 'Home'],
    ['■', 'My Blog'],
    ['◎', 'Search'],
    ['✦', 'Universe'],
    ['▤', 'Settings'],
  ])('%s 아이콘을 %s 항목에 렌더한다', (icon, label) => {
    setup();

    const link = within(nav())
      .getAllByRole('link')
      .find((el) => el.textContent?.includes(label));

    expect(link?.textContent).toContain(icon);
  });

  it('현재 경로의 항목만 활성 스타일을 갖는다', () => {
    setup('/search');

    const links = within(nav()).getAllByRole('link');
    const active = links.filter((el) => el.className.includes('border-l-accent'));

    expect(active).toHaveLength(1);
    expect(active[0].textContent).toContain('Search');
  });

  it('`/`에서는 Home만 활성이다 (end 매칭)', () => {
    setup('/');

    const links = within(nav()).getAllByRole('link');
    const active = links.filter((el) => el.className.includes('border-l-accent'));

    expect(active).toHaveLength(1);
    expect(active[0].textContent).toContain('Home');
  });

  it('Admin 항목이 없다 (PRD §9-K 폐기)', () => {
    setup();
    expect(screen.queryByText(/Admin/i)).not.toBeInTheDocument();
  });

  it('하단에 토큰 저장 위치 캡션을 정확히 렌더한다', () => {
    setup();

    const caption = screen.getByText(/Access Token/);
    expect(caption.textContent).toBe('Access Token · 메모리Refresh Token · HttpOnly Cookie');
  });

  it('폭 210px에 sticky top 20px이다 (240px 화면 전용 패널과 구분)', () => {
    setup();

    expect(nav()).toHaveClass('w-[210px]');
    expect(nav()).toHaveStyle({ position: 'sticky', top: '20px' });
  });
});
