import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../../lib/authContext'

export default function SideNav() {
  const { user } = useAuth()
  const location = useLocation()

  const navItems = [
    { label: 'Home', href: '/', requiredAuth: false },
    { label: 'My Blog', href: '/blog/setup', requiredAuth: true },
    { label: 'Search', href: '/search', requiredAuth: false },
    { label: 'Universe', href: '/settings/universe', requiredAuth: true },
    { label: 'Settings', href: '/settings', requiredAuth: true },
    ...(user?.role === 'ADMIN' ? [{ label: 'Admin', href: '/admin', requiredAuth: true }] : []),
  ]

  const isActive = (href: string) => location.pathname === href

  return (
    <nav className="w-64 bg-bg-panel border-r-2 border-border-purple-dark overflow-y-auto">
      <ul className="flex flex-col">
        {navItems.map((item) => {
          if (item.requiredAuth && !user) {
            return null
          }
          return (
            <li key={item.href}>
              <Link
                to={item.href}
                className={`block px-4 py-3 border-l-4 text-text-primary hover:bg-bg-input transition ${
                  isActive(item.href)
                    ? 'border-l-border-cyan-dark bg-bg-input'
                    : 'border-l-transparent hover:border-l-border-purple-dark'
                }`}
              >
                {item.label}
              </Link>
            </li>
          )
        })}
      </ul>

      {/* Footer info */}
      <div className="p-4 mt-8 border-t border-border-purple-dark text-text-muted text-xs">
        <p>Access Token: Memory</p>
        <p>Refresh Token: HttpOnly Cookie</p>
      </div>
    </nav>
  )
}
