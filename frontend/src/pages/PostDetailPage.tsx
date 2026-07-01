import { useParams } from 'react-router-dom'
import AppShell from '../components/layout/AppShell'

export default function PostDetailPage() {
  const { postId } = useParams()

  return (
    <AppShell showSidebar={false}>
      <div className="p-6 max-w-4xl mx-auto">
        <h1 className="font-display text-3xl text-text-primary mb-4">Post: {postId}</h1>
        <div className="grid grid-cols-3 gap-6">
          {/* Post Content - Main */}
          <div className="col-span-2">
            <div className="bg-bg-panel border-2 border-border-cyan-dark p-6 shadow-card mb-6">
              <p className="text-text-body-cyan">Post content would be rendered here</p>
            </div>
          </div>

          {/* Comments - Right */}
          <div className="bg-bg-panel border-2 border-border-purple-dark p-4 shadow-card">
            <h3 className="font-display text-text-primary mb-4">Comments</h3>
            <p className="text-text-muted">Comments would appear here</p>
          </div>
        </div>
      </div>
    </AppShell>
  )
}
