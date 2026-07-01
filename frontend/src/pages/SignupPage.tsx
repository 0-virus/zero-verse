import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { apiClient } from '../lib/apiClient'

export default function SignupPage() {
  const [formData, setFormData] = useState({
    email: '',
    name: '',
    nickname: '',
    birthDate: '',
    password: '',
    confirmPassword: '',
  })
  const [error, setError] = useState('')
  const navigate = useNavigate()

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    setFormData((prev) => ({ ...prev, [name]: value }))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (formData.password !== formData.confirmPassword) {
      setError('Passwords do not match')
      return
    }

    try {
      const response = await apiClient('/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          email: formData.email,
          name: formData.name,
          nickname: formData.nickname,
          birthDate: formData.birthDate,
          password: formData.password,
        }),
      })

      if (response.success) {
        navigate('/blog/setup')
      } else {
        setError(response.error?.message || 'Sign up failed')
      }
    } catch (err: any) {
      setError(err.message || 'Sign up failed')
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg-space">
      <div className="bg-bg-panel border-4 border-border-cyan-dark p-8 shadow-card max-w-md w-full">
        <h1 className="font-display text-text-primary text-center mb-6">SIGN UP</h1>
        <form onSubmit={handleSubmit} className="space-y-3">
          <input
            type="email"
            name="email"
            placeholder="Email"
            value={formData.email}
            onChange={handleChange}
            className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted"
          />
          <input
            type="text"
            name="name"
            placeholder="Name"
            value={formData.name}
            onChange={handleChange}
            className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted"
          />
          <input
            type="text"
            name="nickname"
            placeholder="Nickname (2-20 chars)"
            value={formData.nickname}
            onChange={handleChange}
            className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted"
          />
          <input
            type="date"
            name="birthDate"
            placeholder="Birth Date"
            value={formData.birthDate}
            onChange={handleChange}
            className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted"
          />
          <input
            type="password"
            name="password"
            placeholder="Password"
            value={formData.password}
            onChange={handleChange}
            className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted"
          />
          <input
            type="password"
            name="confirmPassword"
            placeholder="Confirm Password"
            value={formData.confirmPassword}
            onChange={handleChange}
            className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary placeholder-text-muted"
          />
          {error && <p className="text-border-danger text-sm">{error}</p>}
          <button
            type="submit"
            className="w-full px-4 py-2 bg-bg-button-primary border-2 border-border-cyan-dark text-text-primary font-display hover:bg-opacity-80"
          >
            Sign Up
          </button>
        </form>
        <p className="text-text-muted text-center mt-4">
          Already have account? <Link to="/signin" className="text-border-cyan-light hover:underline">Sign in</Link>
        </p>
      </div>
    </div>
  )
}
