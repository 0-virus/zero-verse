import { useState } from 'react'
import AppShell from '../components/layout/AppShell'

export default function SettingsUniversePage() {
  const [activeTab, setActiveTab] = useState('friends')

  return (
    <AppShell showSidebar={true}>
      <div className="p-6">
        <h1 className="font-display text-3xl text-text-primary mb-6">Universe Management</h1>

        {/* Tabs */}
        <div className="flex gap-4 mb-6 border-b-2 border-border-purple-dark">
          {['friends', 'following', 'followers', 'requests', 'blocks'].map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`px-4 py-2 font-display capitalize ${
                activeTab === tab
                  ? 'border-b-4 border-border-cyan-dark text-text-primary'
                  : 'text-text-muted'
              }`}
            >
              {tab}
            </button>
          ))}
        </div>

        {/* Content */}
        <div className="bg-bg-panel border-2 border-border-cyan-dark p-6 shadow-card">
          <table className="w-full">
            <thead>
              <tr className="border-b-2 border-border-purple-dark">
                <th className="text-left text-text-primary py-2">User</th>
                <th className="text-left text-text-primary py-2">Relation</th>
                <th className="text-left text-text-primary py-2">Blog</th>
                <th className="text-left text-text-primary py-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr className="border-b border-border-purple-dark">
                <td className="py-2 text-text-body-cyan">Sample User</td>
                <td className="py-2 text-text-body-cyan">Friend</td>
                <td className="py-2 text-text-body-cyan">sample-blog</td>
                <td className="py-2">
                  <button className="px-3 py-1 bg-bg-button-danger border-2 border-border-danger text-text-primary mr-2">Remove</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  )
}
