import { useState, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../lib/authContext'

export default function SigninPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [signinSuccess, setSigninSuccess] = useState(false)
  const { signin, user } = useAuth()
  const navigate = useNavigate()

  // Redirect after successful signin
  useEffect(() => {
    if (signinSuccess && user) {
      const destination = user.defaultBlog?.isSetupCompleted === false ? '/blog/setup' : '/'
      navigate(destination)
    }
  }, [signinSuccess, user, navigate])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setIsLoading(true)
    try {
      await signin(email, password)
      setSigninSuccess(true)
    } catch (err: any) {
      // Generic error message that doesn't reveal account existence
      setError('이메일 또는 비밀번호가 올바르지 않습니다.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg-space">
      <div className="bg-bg-panel border-4 border-border-cyan-dark p-8 shadow-card max-w-md w-full">
        <h1 className="font-display text-text-primary text-center mb-6">SIGN IN</h1>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <input
              type="email"
              placeholder="Email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted"
            />
          </div>
          <div>
            <input
              type="password"
              placeholder="Password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted"
            />
          </div>
          {error && <p className="text-border-danger text-sm">{error}</p>}
          <button
            type="submit"
            disabled={isLoading}
            className="w-full px-4 py-2 bg-bg-button-primary border-2 border-border-cyan-dark text-text-primary font-display hover:bg-opacity-80 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isLoading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>
        <p className="text-text-muted text-center mt-4">
          No account? <Link to="/signup" className="text-border-cyan-light hover:underline">Sign up</Link>
        </p>
      </div>
    </div>
  )
}
