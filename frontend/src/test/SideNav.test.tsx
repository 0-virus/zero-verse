import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { SideNav } from '../components/layout/SideNav';

/** 디자인 정본 구조 검증(PRD §6.7). */
describe('SideNav', () => {
  const setup = () =>
    render(
      <MemoryRouter>
        <SideNav />
      </MemoryRouter>,
    );

  it('NAVIGATION 헤더를 렌더한다', () => {
    setup();
    expect(screen.getByRole('heading', { level: 2, name: 'NAVIGATION' })).toBeInTheDocument();
  });

  it('정본이 정한 5개 항목을 순서대로 렌더한다', () => {
    setup();

    const links = within(screen.getByRole('navigation', { name: '주 메뉴' })).getAllByRole('link');
    const labels = links.map((link) => link.textContent?.replace(/[^A-Za-z ]/g, '').trim());

    expect(labels).toEqual(['Home', 'My Blog', 'Search', 'Universe', 'Settings']);
  });

  it('Admin 항목이 없다 (PRD §9-K 폐기)', () => {
    setup();
    expect(screen.queryByText(/Admin/i)).not.toBeInTheDocument();
  });

  it('하단에 토큰 저장 위치 캡션을 렌더한다', () => {
    setup();
    expect(screen.getByText(/Access Token/)).toBeInTheDocument();
    expect(screen.getByText(/HttpOnly Cookie/)).toBeInTheDocument();
  });

  it('폭이 210px이다 (240px 화면 전용 패널과 구분)', () => {
    setup();
    expect(screen.getByRole('navigation', { name: '주 메뉴' })).toHaveClass('w-[210px]');
  });
});
