import React from 'react'
import type { OwnerInfo } from '../types/settings'

interface OwnerProfileCardProps {
  owner: OwnerInfo
}

export const OwnerProfileCard: React.FC<OwnerProfileCardProps> = ({ owner }) => {
  return (
    <div className="bg-bg-panel border-2 border-border-purple-dark p-6 shadow-card">
      <div className="text-center">
        {owner.profileImageUrl && (
          <div className="mb-4">
            <img
              src={owner.profileImageUrl}
              alt={owner.nickname}
              className="w-20 h-20 rounded-full mx-auto border-2 border-border-cyan-dark"
            />
          </div>
        )}
        <h3 className="font-display text-lg text-text-primary mb-2">{owner.nickname}</h3>
        {owner.bio && (
          <p className="text-text-muted text-sm">{owner.bio}</p>
        )}
      </div>
    </div>
  )
}
