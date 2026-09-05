import type { ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { TopBar } from './TopBar';
import { SideNav } from './SideNav';
import { Hero, HeroAvatar } from './Hero';
import { RightPanel } from './RightPanel';
import { ScreenPanel } from './ScreenPanel';
import { AUTH_STARS, PixelRocket, PixelStars, SETUP_STARS } from './StarField';
import { resolveLayout } from '../../lib/layout';
import { useHeroBlog } from '../../lib/heroBlogContext';

/**
 * 앱 셸 = 상단바(PRD §9-L). 사이드바와 컨테이너 폭은 화면별 옵션이다.
 *
 * 레이아웃 경계의 단일 출처는 `lib/layout.ts`(= DESIGN-SYSTEM §6.2)다.
 *
 * - 온보딩(`/signin`, `/signup`, `/blog/setup`): **앱 셸 없이** 전폭 다크 그라디언트 + 중앙 카드.
 *   장식은 화면마다 다르다 — 로그인·회원가입은 별 14개 + 로켓, 초기 설정은 별 11개에 로켓 없음.
 * - `/` 메인 피드: 240px `feed` 히어로 + `210px 1fr 300px` 3열 + 앱 내비.
 * - `/blog/:slug`: 190px `blog` 히어로 + `240px 1fr`(화면 전용 패널).
 * - 그 외: 화면별 max-width와 컬럼 구성.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  const { blog } = useHeroBlog();
  const layout = resolveLayout(pathname);

  if (layout.kind === 'onboarding') {
    const decor = layout.onboardingDecor;
    return (
      <div className="min-h-screen bg-paper">
        <TopBar />
        <div
          data-layout="onboarding"
          style={{ background: 'var(--gradient-auth)', minHeight: 'calc(100vh - 118px)' }}
          className="relative flex items-center justify-center overflow-hidden px-10 py-14"
        >
          {decor && (
            <PixelStars
              top={60}
              left={150}
              size={3}
              color="#fff"
              shadow={decor.stars === 'auth' ? AUTH_STARS : SETUP_STARS}
              duration={2.6}
            />
          )}
          {decor?.rocket && <PixelRocket duration={6} style={{ left: 120, bottom: 120 }} />}
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
          variant={layout.hero.variant}
          height={layout.hero.height}
          eyebrow={layout.hero.variant === 'blog' && blog ? 'MY UNIVERSE / BLOG' : layout.hero.eyebrow}
          title={layout.hero.variant === 'blog' && blog ? blog.title : layout.hero.title}
          description={layout.hero.variant === 'blog' && blog ? `${blog.description || ''} · /blog/${blog.urlSlug}` : layout.hero.description}
          // 소유자 프로필을 반영한다(REQUIREMENTS "블로그 헤더: 소유자 프로필").
          // 이미지가 없으면 이모지로 떨어진다. 액션 버튼은 M5(유니버스 신청)에서 채운다.
          avatar={
            layout.hero.variant === 'blog' ? (
              <HeroAvatar
                profileImageUrl={blog?.owner?.profileImageUrl}
                ownerNickname={blog?.owner?.nickname}
              />
            ) : undefined
          }
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
