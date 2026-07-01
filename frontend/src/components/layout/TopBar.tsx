import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../lib/authContext'

export default function TopBar() {
  const { user, signout } = useAuth()
  const navigate = useNavigate()

  const handleSignout = async () => {
    await signout()
    navigate('/signin')
  }

  return (
    <header className="h-16 bg-bg-navbar border-b-4 border-border-cyan-dark shadow-navbar flex items-center px-6">
      <div className="w-full flex items-center justify-between">
        {/* Logo */}
        <Link to="/" className="font-display text-text-primary text-sm hover:text-border-cyan-light">
          ZERO VERSE
        </Link>

        {/* Search Bar - Center */}
        <div className="flex-1 mx-8 max-w-md">
          <input
            type="text"
            placeholder="Search..."
            className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted shadow-search focus:outline-none"
          />
        </div>

        {/* Right Actions */}
        <div className="flex items-center gap-4">
          {user ? (
            <>
              <Link
                to="/write"
                className="px-4 py-2 bg-bg-button-primary border-2 border-border-cyan-dark text-text-primary hover:bg-opacity-80 shadow-button"
              >
                Write
              </Link>
              <Link
                to="/notifications"
                className="px-3 py-2 bg-bg-button-neutral border-2 border-border-purple-dark text-text-primary hover:bg-opacity-80 shadow-button"
              >
                Notifications
              </Link>
              <Link
                to="/settings"
                className="px-3 py-2 bg-bg-button-neutral border-2 border-border-purple-dark text-text-primary hover:bg-opacity-80 shadow-button"
              >
                Profile
              </Link>
              {user.role === 'ADMIN' && (
                <Link
                  to="/admin"
                  className="px-3 py-2 bg-bg-button-neutral border-2 border-border-admin text-text-primary hover:bg-opacity-80 shadow-button"
                >
                  Admin
                </Link>
              )}
              <button
                onClick={handleSignout}
                className="px-4 py-2 bg-bg-button-danger border-2 border-border-danger text-text-primary hover:bg-opacity-80 shadow-button"
              >
                Signout
              </button>
            </>
          ) : (
            <>
              <Link
                to="/signin"
                className="px-4 py-2 bg-bg-button-primary border-2 border-border-cyan-dark text-text-primary hover:bg-opacity-80 shadow-button"
              >
                Sign In
              </Link>
              <Link
                to="/signup"
                className="px-4 py-2 bg-bg-button-primary border-2 border-border-cyan-dark text-text-primary hover:bg-opacity-80 shadow-button"
              >
                Sign Up
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
