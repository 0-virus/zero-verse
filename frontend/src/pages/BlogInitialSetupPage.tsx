import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiClient } from '../lib/apiClient'

export default function BlogInitialSetupPage() {
  const [formData, setFormData] = useState({
    urlSlug: '',
    title: '',
    description: '',
  })
  const [error, setError] = useState('')
  const navigate = useNavigate()

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target
    setFormData((prev) => ({ ...prev, [name]: value }))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      const response = await apiClient('/blogs/me/initial-setup', {
        method: 'PUT',
        body: JSON.stringify(formData),
      })

      if (response.success) {
        navigate('/')
      } else {
        setError(response.error?.message || 'Setup failed')
      }
    } catch (err: any) {
      setError(err.message || 'Setup failed')
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg-space p-6">
      <div className="bg-bg-panel border-4 border-border-cyan-dark p-8 shadow-card max-w-md w-full">
        <h1 className="font-display text-text-primary text-center mb-6">BLOG SETUP</h1>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <input
              type="text"
              name="urlSlug"
              placeholder="URL Slug (e.g., my-blog)"
              value={formData.urlSlug}
              onChange={handleChange}
              className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted"
            />
          </div>
          <div>
            <input
              type="text"
              name="title"
              placeholder="Blog Title"
              value={formData.title}
              onChange={handleChange}
              className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted"
            />
          </div>
          <div>
            <textarea
              name="description"
              placeholder="Blog Description"
              value={formData.description}
              onChange={handleChange}
              className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted"
              rows={4}
            />
          </div>
          {error && <p className="text-border-danger text-sm">{error}</p>}
          <button
            type="submit"
            className="w-full px-4 py-2 bg-bg-button-primary border-2 border-border-cyan-dark text-text-primary font-display hover:bg-opacity-80"
          >
            Setup Blog
          </button>
        </form>
      </div>
    </div>
  )
}
