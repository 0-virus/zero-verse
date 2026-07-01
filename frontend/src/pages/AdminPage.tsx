import { useState } from 'react'
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
          <p className="text-text-muted">User list coming soon...</p>
        </div>
      </div>
    </AppShell>
  )
}
