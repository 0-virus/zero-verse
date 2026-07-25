import type { ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { TopBar } from './TopBar';
import { SideNav } from './SideNav';
import { Hero } from './Hero';
import { RightPanel } from './RightPanel';
import { ScreenPanel } from './ScreenPanel';
import { AUTH_STARS, PixelRocket, PixelStars } from './StarField';
import { resolveLayout } from '../../lib/layout';

/**
 * 앱 셸 = 상단바(PRD §9-L). 사이드바와 컨테이너 폭은 화면별 옵션이다.
 *
 * 레이아웃 경계의 단일 출처는 `lib/layout.ts`(= DESIGN-SYSTEM §6.2)다.
 *
 * - 온보딩(`/signin`, `/signup`, `/blog/setup`): **앱 셸 없이** 전폭 다크 그라디언트 + 중앙 카드.
 * - `/` 메인 피드: `210px 1fr 300px` 3열 + 앱 내비 사이드바.
 * - 그 외: 화면별 max-width와 컬럼 구성.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  const layout = resolveLayout(pathname);

  if (layout.kind === 'onboarding') {
    return (
      <div className="min-h-screen bg-paper">
        <TopBar />
        <div
          data-layout="onboarding"
          style={{ background: 'var(--gradient-auth)', minHeight: 'calc(100vh - 118px)' }}
          className="relative flex items-center justify-center overflow-hidden px-10 py-14"
        >
          <PixelStars
            top={60}
            left={150}
            size={3}
            color="#fff"
            shadow={AUTH_STARS}
            duration={2.6}
          />
          <PixelRocket duration={6} style={{ left: 120, bottom: 120 }} />
          <div
            data-onboarding-card="true"
            style={{ width: layout.cardWidth }}
            className="relative z-[2]"
          >
            {children}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-paper">
      <TopBar />
      {layout.hero && (
        <Hero
          height={layout.hero.height}
          eyebrow={layout.hero.eyebrow}
          title={layout.hero.title}
          description={layout.hero.description}
        />
      )}
      <div
        data-layout="app"
        style={{
          maxWidth: layout.maxWidth,
          padding: layout.padding,
          display: layout.columns ? 'grid' : 'block',
          gridTemplateColumns: layout.columns ?? undefined,
          gap: layout.columns ? 24 : undefined,
          alignItems: layout.columns ? 'start' : undefined,
        }}
        className="mx-auto"
      >
        {layout.appNav && <SideNav />}
        {layout.screenPanel && <ScreenPanel kind={layout.screenPanel} />}
        <main className="min-w-0">{children}</main>
        {layout.rightPanel && <RightPanel />}
      </div>
    </div>
  );
}
