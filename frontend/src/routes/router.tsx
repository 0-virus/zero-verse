import { createBrowserRouter } from 'react-router-dom'
import type { RouteObject } from 'react-router-dom'
import MainPage from '../pages/MainPage'
import SigninPage from '../pages/SigninPage'
import SignupPage from '../pages/SignupPage'
import BlogInitialSetupPage from '../pages/BlogInitialSetupPage'
import BlogPage from '../pages/BlogPage'
import PostDetailPage from '../pages/PostDetailPage'
import WritePage from '../pages/WritePage'
import EditPage from '../pages/EditPage'
import SettingsProfilePage from '../pages/SettingsProfilePage'
import SettingsUniversePage from '../pages/SettingsUniversePage'
import SettingsPostsPage from '../pages/SettingsPostsPage'
import SearchPage from '../pages/SearchPage'
import NotificationsPage from '../pages/NotificationsPage'
import AdminPage from '../pages/AdminPage'
import AdminUserPage from '../pages/AdminUserPage'
import ProtectedRoute from './ProtectedRoute'
import GuestOnlyRoute from './GuestOnlyRoute'
import AdminRoute from './AdminRoute'
import SetupGuard from './SetupGuard'

const routes: RouteObject[] = [
  {
    path: '/',
    element: <MainPage />,
  },
  {
    path: '/signin',
    element: <GuestOnlyRoute><SigninPage /></GuestOnlyRoute>,
  },
  {
    path: '/signup',
    element: <GuestOnlyRoute><SignupPage /></GuestOnlyRoute>,
  },
  {
    path: '/blog/setup',
    element: <SetupGuard><BlogInitialSetupPage /></SetupGuard>,
  },
  {
    path: '/blog/:blogSlug',
    element: <BlogPage />,
  },
  {
    path: '/blog/:blogSlug/:postId',
    element: <PostDetailPage />,
  },
  {
    path: '/write',
    element: <ProtectedRoute><WritePage /></ProtectedRoute>,
  },
  {
    path: '/edit/:postId',
    element: <ProtectedRoute><EditPage /></ProtectedRoute>,
  },
  {
    path: '/settings',
    element: <ProtectedRoute><SettingsProfilePage /></ProtectedRoute>,
  },
  {
    path: '/settings/universe',
    element: <ProtectedRoute><SettingsUniversePage /></ProtectedRoute>,
  },
  {
    path: '/settings/posts',
    element: <ProtectedRoute><SettingsPostsPage /></ProtectedRoute>,
  },
  {
    path: '/search',
    element: <SearchPage />,
  },
  {
    path: '/notifications',
    element: <ProtectedRoute><NotificationsPage /></ProtectedRoute>,
  },
  {
    path: '/admin',
    element: <AdminRoute><AdminPage /></AdminRoute>,
  },
  {
    path: '/admin/users/:userId',
    element: <AdminRoute><AdminUserPage /></AdminRoute>,
  },
]

const router = createBrowserRouter(routes)

export default router
