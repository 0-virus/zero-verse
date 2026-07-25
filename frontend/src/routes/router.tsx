import { Route, Routes } from 'react-router-dom';
import { AppShell } from '../components/layout/AppShell';
import { GuestOnlyRoute, SetupGuard } from './guards';
import { MainPage } from '../pages/MainPage';
import { SigninPage } from '../pages/SigninPage';
import { SignupPage } from '../pages/SignupPage';
import { BlogSetupPage } from '../pages/BlogSetupPage';
import { BlogPage } from '../pages/BlogPage';
import { PostDetailPage } from '../pages/PostDetailPage';
import { WritePage } from '../pages/WritePage';
import { EditPage } from '../pages/EditPage';
import { SettingsProfilePage } from '../pages/SettingsProfilePage';
import { SettingsUniversePage } from '../pages/SettingsUniversePage';
import { SettingsPostsPage } from '../pages/SettingsPostsPage';
import { SearchPage } from '../pages/SearchPage';
import { NotificationsPage } from '../pages/NotificationsPage';
import { AdminPage } from '../pages/AdminPage';
import { AdminUserPage } from '../pages/AdminUserPage';
import { NotFoundPage } from '../pages/NotFoundPage';

/**
 * 라우트 맵(PRD §7.1). M0는 경로와 레이아웃 경계만 확정한다.
 *
 * 가드 적용(PRD §8.3):
 * - `GuestOnlyRoute` — 로그인 사용자는 `/signin`·`/signup`에 머물지 않는다.
 * - `SetupGuard` — 초기 설정을 안 끝냈으면 `/blog/setup`으로 모은다.
 * - `ProtectedRoute` — 로그인이 필요한 화면.
 *
 * `/`·공개 블로그·게시글·`/search`는 비로그인도 볼 수 있어 가드를 걸지 않는다.
 * `AdminRoute`는 M9다 — 지금 만들면 쓸 화면이 없다.
 *
 * M1의 보호 화면은 모두 초기 설정 흐름에 묶여 있어 `SetupGuard`만 쓴다. `ProtectedRoute`는
 * PRD §8.3이 정의한 가드로 구현·검증해 두었고, 초기 설정과 무관한 보호 화면이 생기는
 * M2 이후에 쓴다.
 */
export function AppRoutes() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<MainPage />} />
        <Route
          path="/signin"
          element={
            <GuestOnlyRoute>
              <SigninPage />
            </GuestOnlyRoute>
          }
        />
        <Route
          path="/signup"
          element={
            <GuestOnlyRoute>
              <SignupPage />
            </GuestOnlyRoute>
          }
        />
        <Route
          path="/blog/setup"
          element={
            <SetupGuard>
              <BlogSetupPage />
            </SetupGuard>
          }
        />
        <Route path="/blog/:blogSlug" element={<BlogPage />} />
        <Route path="/blog/:blogSlug/:postId" element={<PostDetailPage />} />
        <Route
          path="/write"
          element={
            <SetupGuard>
              <WritePage />
            </SetupGuard>
          }
        />
        <Route
          path="/edit/:postId"
          element={
            <SetupGuard>
              <EditPage />
            </SetupGuard>
          }
        />
        <Route
          path="/settings"
          element={
            <SetupGuard>
              <SettingsProfilePage />
            </SetupGuard>
          }
        />
        <Route
          path="/settings/universe"
          element={
            <SetupGuard>
              <SettingsUniversePage />
            </SetupGuard>
          }
        />
        <Route
          path="/settings/posts"
          element={
            <SetupGuard>
              <SettingsPostsPage />
            </SetupGuard>
          }
        />
        <Route path="/search" element={<SearchPage />} />
        <Route
          path="/notifications"
          element={
            <SetupGuard>
              <NotificationsPage />
            </SetupGuard>
          }
        />
        <Route path="/admin" element={<AdminPage />} />
        <Route path="/admin/users/:userId" element={<AdminUserPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AppShell>
  );
}
