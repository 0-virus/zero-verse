import { useParams } from 'react-router-dom'
import AppShell from '../components/layout/AppShell'

export default function BlogPage() {
  const { blogSlug } = useParams()

  return (
    <AppShell showSidebar={false}>
      <div className="p-6">
        <h1 className="font-display text-3xl text-text-primary mb-4">Blog: {blogSlug}</h1>
        <div className="grid grid-cols-4 gap-6">
          {/* Categories - Left */}
          <div className="bg-bg-panel border-2 border-border-purple-dark p-4 shadow-card h-fit">
            <h3 className="font-display text-text-primary mb-4">Categories</h3>
            {/* Category tree would go here */}
            <p className="text-text-muted">Category list</p>
          </div>

          {/* Posts - Center */}
          <div className="col-span-2">
            <div className="bg-bg-panel border-2 border-border-cyan-dark p-4 shadow-card">
              <p className="text-text-muted">Posts loading...</p>
            </div>
          </div>

          {/* Author Info - Right */}
          <div className="bg-bg-panel border-2 border-border-cyan-dark p-4 shadow-card h-fit">
            <h3 className="font-display text-text-primary mb-4">Author</h3>
            <p className="text-text-muted">Author info</p>
          </div>
        </div>
      </div>
    </AppShell>
  )
}
