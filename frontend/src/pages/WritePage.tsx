import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import AppShell from '../components/layout/AppShell'
import { apiClient } from '../lib/apiClient'

export default function WritePage() {
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [category, setCategory] = useState('')
  const [visibility, setVisibility] = useState('PUBLIC')
  const [error, setError] = useState('')
  const navigate = useNavigate()

  const handlePublish = async (publish: boolean) => {
    try {
      const response = await apiClient('/posts', {
        method: 'POST',
        body: JSON.stringify({
          title,
          contentJson: content,
          contentHtml: content,
          blogId: 'TODO',
          categoryId: category || undefined,
          visibility,
          publish,
          tagNames: [],
        }),
      })

      if (response.success) {
        navigate('/')
      } else {
        setError(response.error?.message || 'Failed to publish post')
      }
    } catch (err: any) {
      setError(err.message || 'Failed to publish post')
    }
  }

  return (
    <AppShell showSidebar={false}>
      <div className="flex h-full">
        {/* Editor - Main */}
        <div className="flex-1 p-6 border-r-2 border-border-purple-dark">
          <input
            type="text"
            placeholder="Post Title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted mb-4"
          />
          <textarea
            placeholder="Post Content (Editor mock)"
            value={content}
            onChange={(e) => setContent(e.target.value)}
            className="w-full h-64 px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted"
          />
          {error && <p className="text-border-danger mt-2">{error}</p>}
        </div>

        {/* Publish Settings - Right */}
        <div className="w-64 bg-bg-panel border-l-2 border-border-cyan-dark p-4 space-y-4">
          <h3 className="font-display text-text-primary">Publish Settings</h3>

          <div>
            <label className="block text-text-primary text-sm mb-2">Category</label>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              className="w-full px-2 py-1 bg-bg-input border-2 border-border-cyan-dark text-text-primary"
            >
              <option value="">Uncategorized</option>
            </select>
          </div>

          <div>
            <label className="block text-text-primary text-sm mb-2">Visibility</label>
            <select
              value={visibility}
              onChange={(e) => setVisibility(e.target.value)}
              className="w-full px-2 py-1 bg-bg-input border-2 border-border-cyan-dark text-text-primary"
            >
              <option value="PUBLIC">Public</option>
              <option value="UNIVERSE">친구</option>
              <option value="PRIVATE">Private</option>
            </select>
          </div>

          <div className="pt-4 space-y-2">
            <button
              onClick={() => handlePublish(true)}
              className="w-full px-4 py-2 bg-bg-button-primary border-2 border-border-cyan-dark text-text-primary"
            >
              Publish
            </button>
            <button
              onClick={() => handlePublish(false)}
              className="w-full px-4 py-2 bg-bg-button-neutral border-2 border-border-purple-dark text-text-primary"
            >
              Draft
            </button>
          </div>
        </div>
      </div>
    </AppShell>
  )
}
