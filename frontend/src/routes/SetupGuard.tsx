import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../lib/authContext'

interface SetupGuardProps {
  children: ReactNode
}

export default function SetupGuard({ children }: SetupGuardProps) {
  const { user, isLoading } = useAuth()

  if (isLoading) {
    return <div className="flex items-center justify-center min-h-screen">Loading...</div>
  }

  if (!user) {
    return <Navigate to="/signin" replace />
  }

  // Only allow access if setup is not completed
  if (user.defaultBlog?.isSetupCompleted === true) {
    return <Navigate to="/" replace />
  }

  return children
}
