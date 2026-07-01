import type { Visibility } from './Badge'
import Badge from './Badge'

export interface PostCardProps {
  title: string
  excerpt: string
  authorName: string
  categoryName?: string
  date: string
  viewCount: number
  commentCount: number
  likeCount: number
  visibility: Visibility
  tags: string[]
  thumbnailUrl?: string
  onClick?: () => void
}

export default function PostCard({
  title,
  excerpt,
  authorName,
  categoryName,
  date,
  viewCount,
  commentCount,
  likeCount,
  visibility,
  tags,
  thumbnailUrl,
  onClick,
}: PostCardProps) {
  return (
    <div
      onClick={onClick}
      className="bg-bg-panel border-2 border-border-cyan-dark p-4 shadow-card hover:shadow-glow transition cursor-pointer"
    >
      {/* Header with Badge */}
      <div className="flex justify-between items-start mb-3">
        <h3 className="font-semibold text-text-primary text-lg flex-1">{title}</h3>
        <Badge visibility={visibility} className="ml-2" />
      </div>

      {/* Metadata */}
      <div className="flex gap-4 text-xs text-text-muted mb-3">
        <span>{authorName}</span>
        {categoryName && <span>·</span>}
        {categoryName && <span>{categoryName}</span>}
        <span>·</span>
        <span>{date}</span>
      </div>

      {/* Thumbnail (if provided) */}
      {thumbnailUrl && (
        <div className="mb-3 h-40 overflow-hidden">
          <img
            src={thumbnailUrl}
            alt={title}
            className="w-full h-full object-cover"
          />
        </div>
      )}

      {/* Excerpt */}
      <p className="text-text-body-cyan text-sm mb-3 line-clamp-2">{excerpt}</p>

      {/* Tags */}
      {tags.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-3">
          {tags.slice(0, 3).map((tag) => (
            <span
              key={tag}
              className="px-2 py-1 bg-bg-input border border-border-purple-dark text-text-muted text-xs"
            >
              #{tag}
            </span>
          ))}
          {tags.length > 3 && (
            <span className="px-2 py-1 text-text-muted text-xs">+{tags.length - 3}</span>
          )}
        </div>
      )}

      {/* Footer Stats */}
      <div className="flex gap-4 text-xs text-text-muted border-t border-border-purple-dark pt-3">
        <span>{viewCount} views</span>
        <span>·</span>
        <span>{commentCount} comments</span>
        <span>·</span>
        <span>{likeCount} likes</span>
      </div>
    </div>
  )
}
