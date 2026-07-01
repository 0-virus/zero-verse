import { useParams } from 'react-router-dom'
import AppShell from '../components/layout/AppShell'

export default function AdminUserPage() {
  const { userId } = useParams()

  return (
    <AppShell showSidebar={true}>
      <div className="p-6">
        <h1 className="font-display text-3xl text-text-primary mb-6">User Details: {userId}</h1>

        <div className="grid grid-cols-2 gap-6">
          {/* User Info */}
          <div className="bg-bg-panel border-2 border-border-cyan-dark p-6 shadow-card">
            <h3 className="font-display text-text-primary mb-4">User Information</h3>
            <div className="space-y-3">
              <div>
                <p className="text-text-muted text-sm">Email</p>
                <p className="text-text-primary">user@example.com</p>
              </div>
              <div>
                <p className="text-text-muted text-sm">Nickname</p>
                <p className="text-text-primary">sample-user</p>
              </div>
              <div>
                <p className="text-text-muted text-sm">Name</p>
                <p className="text-text-primary">Sample User</p>
              </div>
              <div>
                <p className="text-text-muted text-sm">Joined</p>
                <p className="text-text-primary">2024-01-01</p>
              </div>
            </div>
          </div>

          {/* Admin Actions */}
          <div className="bg-bg-panel border-2 border-border-admin p-6 shadow-card border-l-8">
            <h3 className="font-display text-border-admin mb-4">Admin Actions</h3>
            <div className="space-y-3">
              <div>
                <label className="block text-text-primary mb-2">Role</label>
                <select className="w-full px-4 py-2 bg-bg-input border-2 border-border-admin text-text-primary">
                  <option>USER</option>
                  <option>ADMIN</option>
                </select>
              </div>
              <div>
                <label className="block text-text-primary mb-2">Status</label>
                <select className="w-full px-4 py-2 bg-bg-input border-2 border-border-admin text-text-primary">
                  <option>ACTIVE</option>
                  <option>SUSPENDED</option>
                </select>
              </div>
              <div className="pt-4 space-y-2">
                <button className="w-full px-4 py-2 bg-bg-button-primary border-2 border-border-cyan-dark text-text-primary">
                  Save Changes
                </button>
                <button className="w-full px-4 py-2 bg-bg-button-danger border-2 border-border-danger text-text-primary">
                  Delete User
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Statistics */}
        <div className="mt-6 grid grid-cols-3 gap-4">
          <div className="bg-bg-panel border-2 border-border-purple-dark p-4 text-center shadow-card">
            <p className="text-text-muted text-sm">Blogs</p>
            <p className="font-display text-3xl text-text-primary">1</p>
          </div>
          <div className="bg-bg-panel border-2 border-border-purple-dark p-4 text-center shadow-card">
            <p className="text-text-muted text-sm">Posts</p>
            <p className="font-display text-3xl text-text-primary">5</p>
          </div>
          <div className="bg-bg-panel border-2 border-border-purple-dark p-4 text-center shadow-card">
            <p className="text-text-muted text-sm">Comments</p>
            <p className="font-display text-3xl text-text-primary">12</p>
          </div>
        </div>
      </div>
    </AppShell>
  )
}
