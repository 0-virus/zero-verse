import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useBlogSettings } from '../hooks/useBlogSettings'
import type { BlogInitialSetupRequest } from '../types/settings'

export default function BlogInitialSetupPage() {
  const [formData, setFormData] = useState<BlogInitialSetupRequest>({
    urlSlug: '',
    title: '',
    description: '',
  })
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const navigate = useNavigate()
  const { initialSetup } = useBlogSettings()

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target
    setFormData((prev) => ({ ...prev, [name]: value }))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setIsSubmitting(true)

    try {
      const result = await initialSetup(formData)
      if (result) {
        // Success - navigate to home or blog
        navigate('/')
      } else {
        // Error already set by hook
        setError('블로그 초기 설정에 실패했습니다.')
      }
    } catch (err: any) {
      setError(err.message || '블로그 초기 설정에 실패했습니다.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg-space p-6">
      <div className="bg-bg-panel border-4 border-border-cyan-dark p-8 shadow-card max-w-md w-full">
        <h1 className="font-display text-text-primary text-center mb-2">BLOG SETUP</h1>
        <p className="text-text-muted text-center text-sm mb-6">
          빈 칸은 서버에서 자동으로 채워집니다
        </p>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-text-primary text-sm mb-1">URL Slug (선택)</label>
            <input
              type="text"
              name="urlSlug"
              placeholder="e.g., my-blog"
              value={formData.urlSlug}
              onChange={handleChange}
              disabled={isSubmitting}
              className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted disabled:opacity-50"
            />
          </div>
          <div>
            <label className="block text-text-primary text-sm mb-1">블로그 제목 (선택)</label>
            <input
              type="text"
              name="title"
              placeholder="기본값: {닉네임}의 블로그"
              value={formData.title}
              onChange={handleChange}
              disabled={isSubmitting}
              className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted disabled:opacity-50"
            />
          </div>
          <div>
            <label className="block text-text-primary text-sm mb-1">설명 (선택)</label>
            <textarea
              name="description"
              placeholder="블로그 설명..."
              value={formData.description}
              onChange={handleChange}
              disabled={isSubmitting}
              className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted disabled:opacity-50"
              rows={4}
            />
          </div>
          {error && (
            <div className="bg-border-danger bg-opacity-10 border-2 border-border-danger p-3 rounded text-border-danger text-sm">
              {error}
            </div>
          )}
          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full px-4 py-2 bg-bg-button-primary border-2 border-border-cyan-dark text-text-primary font-display hover:bg-opacity-80 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isSubmitting ? '설정 중...' : '초기 설정 완료'}
          </button>
        </form>
      </div>
    </div>
  )
}
