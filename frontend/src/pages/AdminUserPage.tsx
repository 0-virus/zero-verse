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
            <p className="text-text-muted">User details coming soon...</p>
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

      </div>
    </AppShell>
  )
}
