import { useEffect, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { adminAccessErrorMessage, useAdminAccess } from '../features/admin/AdminAccessProvider';
import { useAuth } from '../lib/authContext';

/**
 * 라우팅 가드(PRD §8.3).
 *
 * <p>세 가드 모두 `isLoading` 동안에는 판단을 미룬다. 초기 세션 복구가 끝나기 전에 리다이렉트하면
 * 로그인 상태인데도 `/signin`으로 튕기는 깜빡임이 생긴다.
 *
 * <p>관리자 가드(AdminRoute)는 AuthUser.role을 최종 권한으로 신뢰하지 않고, 현재 DB 권한을
 * 목록 size=1 probe로 확인한 뒤 민감한 자식 화면을 렌더한다(M9 A-v2).
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
  const state = location.state;
  const completionTarget =
    isSetupPage &&
    typeof state === 'object' &&
    state !== null &&
    'setupCompletionTo' in state &&
    state.setupCompletionTo === 'blog' &&
    user.defaultBlog.urlSlug
      ? `/blog/${user.defaultBlog.urlSlug}`
      : null;
  const recoveryTarget =
    isSetupPage &&
    typeof state === 'object' &&
    state !== null &&
    'setupRecoveryTo' in state &&
    state.setupRecoveryTo === '/settings/posts'
      ? '/settings/posts'
      : null;

  if (isSetupPage && isSetupCompleted) {
    return <Navigate to={completionTarget ?? recoveryTarget ?? '/'} replace />;
  }
  if (!isSetupPage && !isSetupCompleted) {
    return <Navigate to="/blog/setup" replace />;
  }
  return <>{children}</>;
}

/** 현재 DB의 ADMIN 권한을 확인한 사용자만 관리자 화면에 진입시킨다. */
export function AdminRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();
  const location = useLocation();
  const access = useAdminAccess();

  useEffect(() => {
    if (!isLoading && isAuthenticated && access.status === 'idle') void access.probe();
  }, [access.probe, access.status, isAuthenticated, isLoading]);

  if (isLoading) return <GuardPending />;
  if (!isAuthenticated) {
    return <Navigate to="/signin" state={{ from: location }} replace />;
  }

  if (access.status === 'idle' || access.status === 'loading') {
    return <GuardPending />;
  }

  if (access.status === 'denied') {
    return (
      <section aria-label="관리자 권한 없음" className="px-6 py-12">
        <h1 className="text-[23px] font-bold">관리자 화면에 접근할 수 없습니다</h1>
        <p className="mt-2 text-[13px] text-text-body">
          {adminAccessErrorMessage(access.error)}
        </p>
      </section>
    );
  }

  if (access.status === 'error') {
    return (
      <section aria-label="관리자 권한 확인 오류" className="px-6 py-12">
        <h1 className="text-[23px] font-bold">관리자 권한을 확인하지 못했습니다</h1>
        <p role="alert" className="mt-2 text-[13px] text-danger">
          {adminAccessErrorMessage(access.error)}
        </p>
        <button
          type="button"
          onClick={() => void access.retry()}
          className="mt-5 border-[3px] border-ink bg-surface px-[18px] py-2 text-[13px] font-bold shadow-btn hover:bg-surface-raise"
        >
          다시 시도
        </button>
      </section>
    );
  }

  return <>{children}</>;
}
