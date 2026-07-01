import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../lib/authContext'

interface GuestOnlyRouteProps {
  children: ReactNode
}

export default function GuestOnlyRoute({ children }: GuestOnlyRouteProps) {
  const { user, isLoading } = useAuth()

  if (isLoading) {
    return <div className="flex items-center justify-center min-h-screen">Loading...</div>
  }

  if (user) {
    return <Navigate to="/" replace />
  }

  return children
}
