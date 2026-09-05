import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { TopBar } from '../components/layout/TopBar';

describe('TopBar', () => {
  const setup = () =>
    render(
      <MemoryRouter>
        <TopBar />
      </MemoryRouter>,
    );

  it('로고·검색·글쓰기·알림·프로필을 렌더한다', () => {
    setup();
    expect(screen.getByRole('link', { name: 'ZEROVERSE' })).toBeInTheDocument();
    expect(screen.getByRole('searchbox', { name: '검색' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '✎ 글쓰기' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '알림' })).toBeInTheDocument();
    const profileLink = screen.getByRole('link', { name: '로그인' });
    expect(profileLink).toHaveAttribute('href', '/signin');
  });

  it('관리자 항목을 노출하지 않는다 (PRD §9-K 폐기)', () => {
    setup();
    expect(screen.queryByText(/관리자/)).not.toBeInTheDocument();
    expect(screen.queryByText(/ADMIN/i)).not.toBeInTheDocument();
  });
});
