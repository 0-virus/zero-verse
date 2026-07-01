import type { ReactNode } from 'react'
import TopBar from './TopBar'
import SideNav from './SideNav'

interface AppShellProps {
  children: ReactNode
  showSidebar?: boolean
}

export default function AppShell({ children, showSidebar = true }: AppShellProps) {
  return (
    <div className="flex flex-col h-screen bg-bg-space">
      {/* Top Bar */}
      <TopBar />

      {/* Main Content */}
      <div className="flex flex-1 overflow-hidden">
        {/* Side Navigation */}
        {showSidebar && <SideNav />}

        {/* Content */}
        <main className={`flex-1 overflow-auto ${showSidebar ? 'ml-0' : ''}`}>
          {children}
        </main>
      </div>
    </div>
  )
}
