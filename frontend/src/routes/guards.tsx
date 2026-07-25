import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../lib/authContext';

/**
 * 라우팅 가드(PRD §8.3).
 *
 * <p>세 가드 모두 `isLoading` 동안에는 판단을 미룬다. 초기 세션 복구가 끝나기 전에 리다이렉트하면
 * 로그인 상태인데도 `/signin`으로 튕기는 깜빡임이 생긴다.
 *
 * <p>관리자 가드(AdminRoute)는 M9다 — 지금 만들면 쓸 화면이 없다.
 */

/** 판정을 미루는 동안 보여줄 자리. 레이아웃이 흔들리지 않게 최소한만 차지한다. */
function GuardPending() {
  return (
    <div data-guard-pending="true" className="px-6 py-16 text-center">
      <p className="text-[13px] text-text-muted">불러오는 중…</p>
    </div>
  );
}

/** 로그인이 필요한 화면. 미인증이면 `/signin`으로 보낸다. */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <GuardPending />;
  }
  if (!isAuthenticated) {
    // 로그인 후 원래 가려던 곳으로 돌려보내기 위해 위치를 남긴다.
    return <Navigate to="/signin" state={{ from: location }} replace />;
  }
  return <>{children}</>;
}

/**
 * 로그인한 사용자가 보면 안 되는 화면(`/signin`, `/signup`).
 *
 * <p>초기 설정을 안 끝냈으면 `/blog/setup`, 끝냈으면 `/`로 보낸다.
 */
export function GuestOnlyRoute({ children }: { children: ReactNode }) {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return <GuardPending />;
  }
  if (user) {
    return <Navigate to={user.defaultBlog.isSetupCompleted ? '/' : '/blog/setup'} replace />;
  }
  return <>{children}</>;
}

/**
 * 초기 설정 흐름을 강제한다.
 *
 * <p>- 미인증 → `/signin`
 * <p>- `/blog/setup`인데 이미 설정 완료 → `/`
 * <p>- 설정 미완료인데 다른 보호 화면 → `/blog/setup`
 */
export function SetupGuard({ children }: { children: ReactNode }) {
  const { user, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <GuardPending />;
  }
  if (!user) {
    return <Navigate to="/signin" state={{ from: location }} replace />;
  }

  const isSetupPage = location.pathname === '/blog/setup';
  const isSetupCompleted = user.defaultBlog.isSetupCompleted;

  if (isSetupPage && isSetupCompleted) {
    return <Navigate to="/" replace />;
  }
  if (!isSetupPage && !isSetupCompleted) {
    return <Navigate to="/blog/setup" replace />;
  }
  return <>{children}</>;
}
