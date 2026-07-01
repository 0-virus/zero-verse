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
            <ul className="space-y-2">
              <li className="px-3 py-2 bg-bg-input border-l-4 border-border-cyan-dark text-text-primary">
                [DEFAULT] Uncategorized
              </li>
              <li className="px-3 py-2 hover:bg-bg-input border-l-4 border-transparent text-text-body-cyan cursor-pointer">
                [GENERAL] General
              </li>
            </ul>
            <button className="mt-4 w-full px-4 py-2 bg-bg-button-primary border-2 border-border-cyan-dark text-text-primary">
              Add Category
            </button>
          </div>

          {/* Category Details - Right */}
          <div className="col-span-2 bg-bg-panel border-2 border-border-cyan-dark p-4 shadow-card">
            <h3 className="font-display text-text-primary mb-4">Category Details</h3>
            <div className="space-y-4">
              <div>
                <label className="block text-text-primary mb-2">Name</label>
                <input type="text" disabled className="w-full px-4 py-2 bg-bg-input border-2 border-border-purple-dark text-text-muted" placeholder="Select a category" />
              </div>
              <div>
                <label className="block text-text-primary mb-2">Type</label>
                <select disabled className="w-full px-4 py-2 bg-bg-input border-2 border-border-purple-dark text-text-muted">
                  <option>[DEFAULT]</option>
                  <option>[GENERAL]</option>
                  <option>[LOCKED]</option>
                </select>
              </div>
              <p className="text-text-muted text-sm">Select a category to edit</p>
            </div>
          </div>
        </div>
      </div>
    </AppShell>
  )
}
