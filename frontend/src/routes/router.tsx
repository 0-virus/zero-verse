import { Route, Routes } from 'react-router-dom';
import { AppShell } from '../components/layout/AppShell';
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
 * ProtectedRoute / GuestOnlyRoute / SetupGuard / AdminRoute는 M1에서 추가한다(PRD §8.3, §10 M1).
 */
export function AppRoutes() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<MainPage />} />
        <Route path="/signin" element={<SigninPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/blog/setup" element={<BlogSetupPage />} />
        <Route path="/blog/:blogSlug" element={<BlogPage />} />
        <Route path="/blog/:blogSlug/:postId" element={<PostDetailPage />} />
        <Route path="/write" element={<WritePage />} />
        <Route path="/edit/:postId" element={<EditPage />} />
        <Route path="/settings" element={<SettingsProfilePage />} />
        <Route path="/settings/universe" element={<SettingsUniversePage />} />
        <Route path="/settings/posts" element={<SettingsPostsPage />} />
        <Route path="/search" element={<SearchPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="/admin" element={<AdminPage />} />
        <Route path="/admin/users/:userId" element={<AdminUserPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AppShell>
  );
}
