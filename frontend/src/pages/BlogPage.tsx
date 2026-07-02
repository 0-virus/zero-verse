import { useParams } from 'react-router-dom'
import { useEffect, useState } from 'react'
import AppShell from '../components/layout/AppShell'
import { BlogHeader } from '../components/BlogHeader'
import { OwnerProfileCard } from '../components/OwnerProfileCard'
import { useBlogPublic } from '../hooks/useBlogPublic'
import type { BlogPublicResponse } from '../types/settings'

export default function BlogPage() {
  const { blogSlug } = useParams()
  const { getPublicBlog, isLoading, error } = useBlogPublic()
  const [blog, setBlog] = useState<BlogPublicResponse | null>(null)

  useEffect(() => {
    if (blogSlug) {
      loadBlog()
    }
  }, [blogSlug])

  const loadBlog = async () => {
    if (!blogSlug) return
    const result = await getPublicBlog(blogSlug)
    if (result) {
      setBlog(result)
    }
  }

  if (isLoading) {
    return (
      <AppShell showSidebar={false}>
        <div className="p-6 flex items-center justify-center min-h-96">
          <p className="text-text-muted">블로그 로딩 중...</p>
        </div>
      </AppShell>
    )
  }

  if (error || !blog) {
    return (
      <AppShell showSidebar={false}>
        <div className="p-6">
          <div className="max-w-md mx-auto bg-bg-panel border-2 border-border-danger p-6 shadow-card text-center">
            <h1 className="font-display text-2xl text-text-primary mb-4">Not Found</h1>
            <p className="text-text-muted">블로그를 찾을 수 없습니다.</p>
          </div>
        </div>
      </AppShell>
    )
  }

  return (
    <AppShell showSidebar={false}>
      <div className="p-6">
        {/* Blog Header */}
        <BlogHeader title={blog.title} description={blog.description} />

        <div className="grid grid-cols-4 gap-6">
          {/* Categories - Left */}
          <div className="bg-bg-panel border-2 border-border-purple-dark p-4 shadow-card h-fit">
            <h3 className="font-display text-text-primary mb-4">Categories</h3>
            <p className="text-text-muted text-sm">카테고리 (M4)</p>
          </div>

          {/* Posts - Center */}
          <div className="col-span-2">
            <div className="bg-bg-panel border-2 border-border-cyan-dark p-6 shadow-card">
              <h3 className="font-display text-text-primary mb-4">Posts</h3>
              <p className="text-text-muted text-sm text-center py-8">
                게시글이 없습니다.<br />
                <span className="text-xs">(게시글 목록은 M4에서 구현됩니다)</span>
              </p>
            </div>
          </div>

          {/* Owner Info - Right */}
          <div className="h-fit">
            <OwnerProfileCard owner={blog.owner} />
          </div>
        </div>
      </div>
    </AppShell>
  )
}
