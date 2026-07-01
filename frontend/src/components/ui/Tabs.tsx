import type { ReactNode } from 'react'

interface TabsProps {
  tabs: { label: string; id: string }[]
  activeTab: string
  onTabChange: (id: string) => void
  children: ReactNode
}

export default function Tabs({ tabs, activeTab, onTabChange, children }: TabsProps) {
  return (
    <div>
      <div className="flex gap-4 border-b-2 border-border-purple-dark">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => onTabChange(tab.id)}
            className={`px-4 py-2 font-display transition ${
              activeTab === tab.id
                ? 'border-b-4 border-border-cyan-dark text-text-primary'
                : 'text-text-muted hover:text-text-primary'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div className="mt-4">{children}</div>
    </div>
  )
}
