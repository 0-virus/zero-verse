import { useState } from 'react'
import AppShell from '../components/layout/AppShell'
import { useAuth } from '../lib/authContext'
import { apiClient } from '../lib/apiClient'

export default function SettingsProfilePage() {
  const { user } = useAuth()
  const [activeTab, setActiveTab] = useState('profile')
  const [formData, setFormData] = useState({
    name: user?.name || '',
    birthDate: user?.birthDate || '',
    profileImageUrl: user?.profileImageUrl || '',
  })
  const [error, setError] = useState('')

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    setFormData((prev) => ({ ...prev, [name]: value }))
  }

  const handleSave = async () => {
    try {
      const response = await apiClient('/users/me', {
        method: 'PUT',
        body: JSON.stringify(formData),
      })

      if (!response.success) {
        setError(response.error?.message || 'Failed to save')
      }
    } catch (err: any) {
      setError(err.message || 'Failed to save')
    }
  }

  return (
    <AppShell showSidebar={true}>
      <div className="p-6">
        <h1 className="font-display text-3xl text-text-primary mb-6">Settings</h1>

        {/* Tabs */}
        <div className="flex gap-4 mb-6 border-b-2 border-border-purple-dark">
          <button
            onClick={() => setActiveTab('profile')}
            className={`px-4 py-2 font-display ${
              activeTab === 'profile'
                ? 'border-b-4 border-border-cyan-dark text-text-primary'
                : 'text-text-muted'
            }`}
          >
            Profile
          </button>
          <button
            onClick={() => setActiveTab('blog')}
            className={`px-4 py-2 font-display ${
              activeTab === 'blog'
                ? 'border-b-4 border-border-cyan-dark text-text-primary'
                : 'text-text-muted'
            }`}
          >
            Blog
          </button>
          <button
            onClick={() => setActiveTab('security')}
            className={`px-4 py-2 font-display ${
              activeTab === 'security'
                ? 'border-b-4 border-border-cyan-dark text-text-primary'
                : 'text-text-muted'
            }`}
          >
            Security
          </button>
        </div>

        {/* Profile Tab */}
        {activeTab === 'profile' && (
          <div className="bg-bg-panel border-2 border-border-cyan-dark p-6 shadow-card">
            <h3 className="font-display text-text-primary mb-4">User Profile</h3>
            <div className="space-y-4">
              <div>
                <label className="block text-text-primary mb-2">Email (read-only)</label>
                <input type="email" value={user?.email || ''} disabled className="w-full px-4 py-2 bg-bg-input border-2 border-border-purple-dark text-text-muted" />
              </div>
              <div>
                <label className="block text-text-primary mb-2">Name</label>
                <input type="text" name="name" value={formData.name} onChange={handleChange} className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary" />
              </div>
              <div>
                <label className="block text-text-primary mb-2">Birth Date</label>
                <input type="date" name="birthDate" value={formData.birthDate} onChange={handleChange} className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary" />
              </div>
              {error && <p className="text-border-danger">{error}</p>}
              <button onClick={handleSave} className="px-4 py-2 bg-bg-button-primary border-2 border-border-cyan-dark text-text-primary">
                Save
              </button>
            </div>
          </div>
        )}

        {/* Blog Tab */}
        {activeTab === 'blog' && (
          <div className="bg-bg-panel border-2 border-border-cyan-dark p-6 shadow-card">
            <h3 className="font-display text-text-primary mb-4">Blog Settings</h3>
            <p className="text-text-muted">Blog settings would appear here</p>
          </div>
        )}

        {/* Security Tab */}
        {activeTab === 'security' && (
          <div className="bg-bg-panel border-2 border-border-cyan-dark p-6 shadow-card">
            <h3 className="font-display text-text-primary mb-4">Security</h3>
            <p className="text-text-muted">Password change form would appear here</p>
          </div>
        )}
      </div>
    </AppShell>
  )
}
