import { useState } from 'react'
import AppShell from '../components/layout/AppShell'

export default function NotificationsPage() {
  const [activeTab, setActiveTab] = useState('all')

  return (
    <AppShell showSidebar={false}>
      <div className="p-6 max-w-4xl mx-auto">
        <h1 className="font-display text-3xl text-text-primary mb-6">Notifications</h1>

        {/* Tabs */}
        <div className="flex gap-4 mb-6 border-b-2 border-border-purple-dark">
          {['all', 'unread', 'comment', 'relation'].map((tab) => (
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

        {/* Actions */}
        <div className="flex gap-4 mb-6">
          <button className="px-4 py-2 bg-bg-button-primary border-2 border-border-cyan-dark text-text-primary">
            Mark All as Read
          </button>
          <button className="px-4 py-2 bg-bg-button-neutral border-2 border-border-purple-dark text-text-primary">
            Delete Read
          </button>
        </div>

        {/* Notifications List */}
        <div className="bg-bg-panel border-2 border-border-cyan-dark shadow-card">
          <table className="w-full">
            <thead>
              <tr className="border-b-2 border-border-purple-dark">
                <th className="text-left text-text-primary py-3 px-4">Type</th>
                <th className="text-left text-text-primary py-3 px-4">Content</th>
                <th className="text-left text-text-primary py-3 px-4">Date</th>
                <th className="text-left text-text-primary py-3 px-4">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr className="border-b border-border-purple-dark">
                <td className="py-3 px-4 text-text-body-cyan">Comment</td>
                <td className="py-3 px-4 text-text-body-cyan">Someone commented on your post</td>
                <td className="py-3 px-4 text-text-muted">2h ago</td>
                <td className="py-3 px-4">
                  <button className="px-2 py-1 bg-bg-button-neutral border border-border-cyan-dark text-text-primary text-sm mr-2">
                    View
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  )
}
