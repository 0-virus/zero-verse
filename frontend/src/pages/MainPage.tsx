import AppShell from '../components/layout/AppShell'
import { useAuth } from '../lib/authContext'

export default function MainPage() {
  const { user } = useAuth()

  return (
    <AppShell showSidebar={true}>
      <div className="p-6">
        <h1 className="text-3xl font-display text-text-primary mb-4">유니버스 새 소식</h1>
        <div className="grid grid-cols-3 gap-6">
          {/* Main Feed */}
          <div className="col-span-2">
            <div className="mb-6">
              <div className="flex gap-4 mb-4">
                <button className="px-4 py-2 border-2 border-border-cyan-dark bg-bg-panel text-text-primary">전체</button>
                <button className="px-4 py-2 border-2 border-border-purple-dark bg-bg-panel text-text-primary">친구</button>
                <button className="px-4 py-2 border-2 border-border-purple-dark bg-bg-panel text-text-primary">내가 발견한</button>
              </div>
            </div>
            {/* Post cards would go here */}
            <div className="border-2 border-border-cyan-dark bg-bg-panel p-4">
              <p className="text-text-muted">{user ? 'Loading posts...' : 'Sign in to see your universe feed'}</p>
            </div>
          </div>

          {/* Right Panel */}
          <div className="col-span-1">
            {user && (
              <div className="bg-bg-panel border-2 border-border-purple-dark p-4 shadow-card mb-4">
                <h3 className="font-display text-text-primary mb-2">내 블로그</h3>
                <p className="text-text-muted text-sm">Blog setup pending</p>
              </div>
            )}
            <div className="bg-bg-panel border-2 border-border-cyan-dark p-4 shadow-card">
              <h3 className="font-display text-text-primary mb-2">최근 알림</h3>
              <p className="text-text-muted text-sm">No notifications</p>
            </div>
          </div>
        </div>
      </div>
    </AppShell>
  )
}
