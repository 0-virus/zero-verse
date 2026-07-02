import React from 'react'

interface BlogHeaderProps {
  title: string
  description?: string
}

export const BlogHeader: React.FC<BlogHeaderProps> = ({ title, description }) => {
  return (
    <div className="bg-bg-panel border-2 border-border-cyan-dark p-6 shadow-card mb-6">
      <h1 className="font-display text-2xl text-text-primary mb-2">{title}</h1>
      {description && (
        <p className="text-text-muted text-sm">{description}</p>
      )}
    </div>
  )
}
