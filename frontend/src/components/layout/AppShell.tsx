import type { ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { TopBar } from './TopBar';
import { SideNav } from './SideNav';

/**
 * 앱 셸 = 상단바(PRD §9-L). 사이드바는 화면별 옵션이며 `/`에서만 렌더한다(PRD §6.7).
 *
 * 온보딩 경로(`/signin`, `/signup`, `/blog/setup`)는 다크 레이아웃이며 사이드바가 없다.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  const showSideNav = pathname === '/';

  return (
    <div className="min-h-screen bg-paper">
      <TopBar />
      <div className="mx-auto flex max-w-[1440px] gap-8 px-8 py-8">
        {showSideNav && <SideNav />}
        <main className="min-w-0 flex-1">{children}</main>
      </div>
    </div>
  );
}
