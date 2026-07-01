import { useState } from 'react'
import AppShell from '../components/layout/AppShell'

export default function SearchPage() {
  const [keyword, setKeyword] = useState('')
  const [type, setType] = useState('ALL')

  return (
    <AppShell showSidebar={true}>
      <div className="p-6">
        <h1 className="font-display text-3xl text-text-primary mb-6">Search</h1>

        {/* Search Bar */}
        <div className="bg-bg-panel border-2 border-border-cyan-dark p-4 shadow-card mb-6">
          <div className="flex gap-4">
            <input
              type="text"
              placeholder="Search posts, blogs, users, tags..."
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              className="flex-1 px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted"
            />
            <button className="px-6 py-2 bg-bg-button-primary border-2 border-border-cyan-dark text-text-primary">
              Search
            </button>
          </div>
        </div>

        {/* Type Tabs */}
        <div className="flex gap-4 mb-6 border-b-2 border-border-purple-dark">
          {['ALL', 'POST', 'BLOG', 'USER', 'TAG'].map((t) => (
            <button
              key={t}
              onClick={() => setType(t)}
              className={`px-4 py-2 font-display ${
                type === t
                  ? 'border-b-4 border-border-cyan-dark text-text-primary'
                  : 'text-text-muted'
              }`}
            >
              {t}
            </button>
          ))}
        </div>

        {/* Results */}
        <div className="bg-bg-panel border-2 border-border-cyan-dark p-6 shadow-card">
          <p className="text-text-muted">Search results will appear here</p>
        </div>
      </div>
    </AppShell>
  )
}
