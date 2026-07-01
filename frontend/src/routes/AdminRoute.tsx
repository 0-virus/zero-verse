import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../lib/authContext'

interface AdminRouteProps {
  children: ReactNode
}

export default function AdminRoute({ children }: AdminRouteProps) {
  const { user, isLoading } = useAuth()

  if (isLoading) {
    return <div className="flex items-center justify-center min-h-screen">Loading...</div>
  }

  if (!user || user.role !== 'ADMIN') {
    return <Navigate to="/" replace />
  }

  return children
}
