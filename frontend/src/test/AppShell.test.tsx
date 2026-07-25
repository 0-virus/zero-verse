import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppShell } from '../components/layout/AppShell';

function renderShell(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppShell>
        <p>본문</p>
      </AppShell>
    </MemoryRouter>,
  );
}

describe('AppShell', () => {
  it('모든 화면에서 TopBar를 렌더한다', () => {
    renderShell('/search');
    expect(screen.getByRole('link', { name: 'ZEROVERSE' })).toBeInTheDocument();
  });

  it('/ 에서만 SideNav를 렌더한다', () => {
    renderShell('/');
    expect(screen.getByRole('navigation', { name: '주 메뉴' })).toBeInTheDocument();
  });

  it.each(['/signin', '/signup', '/blog/setup', '/search', '/settings'])(
    '%s 에는 SideNav를 렌더하지 않는다',
    (path) => {
      renderShell(path);
      expect(screen.queryByRole('navigation', { name: '주 메뉴' })).not.toBeInTheDocument();
    },
  );
});
