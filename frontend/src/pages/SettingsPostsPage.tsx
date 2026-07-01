import AppShell from '../components/layout/AppShell'

export default function SettingsPostsPage() {
  return (
    <AppShell showSidebar={true}>
      <div className="p-6">
        <h1 className="font-display text-3xl text-text-primary mb-6">Category Management</h1>

        <div className="grid grid-cols-3 gap-6">
          {/* Categories Tree - Left */}
          <div className="col-span-1 bg-bg-panel border-2 border-border-purple-dark p-4 shadow-card">
            <h3 className="font-display text-text-primary mb-4">Categories</h3>
            <p className="text-text-muted">Category tree coming soon...</p>
          </div>

          {/* Category Details - Right */}
          <div className="col-span-2 bg-bg-panel border-2 border-border-cyan-dark p-4 shadow-card">
            <h3 className="font-display text-text-primary mb-4">Category Details</h3>
            <p className="text-text-muted">Select a category to edit</p>
          </div>
        </div>
      </div>
    </AppShell>
  )
}
