import { useState } from 'react'
import { Link } from 'react-router-dom'
import AppShell from '../components/layout/AppShell'

export default function AdminPage() {
  const [search, setSearch] = useState('')

  return (
    <AppShell showSidebar={true}>
      <div className="p-6">
        <h1 className="font-display text-3xl text-text-primary mb-6">Admin - User Management</h1>

        {/* Search Bar */}
        <div className="bg-bg-panel border-2 border-border-cyan-dark p-4 shadow-card mb-6">
          <input
            type="text"
            placeholder="Search by email, name, or blog slug..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted"
          />
        </div>

        {/* Users Table */}
        <div className="bg-bg-panel border-2 border-border-admin p-4 shadow-card border-l-8">
          <table className="w-full">
            <thead>
              <tr className="border-b-2 border-border-admin">
                <th className="text-left text-border-admin py-3 px-4">Email</th>
                <th className="text-left text-border-admin py-3 px-4">Nickname</th>
                <th className="text-left text-border-admin py-3 px-4">Role</th>
                <th className="text-left text-border-admin py-3 px-4">Status</th>
                <th className="text-left text-border-admin py-3 px-4">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr className="border-b border-border-admin">
                <td className="py-3 px-4 text-text-body-cyan">user@example.com</td>
                <td className="py-3 px-4 text-text-body-cyan">sample-user</td>
                <td className="py-3 px-4 text-text-body-cyan">USER</td>
                <td className="py-3 px-4 text-text-body-cyan">ACTIVE</td>
                <td className="py-3 px-4">
                  <Link to="/admin/users/1" className="px-3 py-1 bg-bg-button-neutral border-2 border-border-admin text-border-admin hover:bg-opacity-80">
                    View
                  </Link>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  )
}
