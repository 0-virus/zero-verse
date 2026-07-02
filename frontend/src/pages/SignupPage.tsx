import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../lib/authContext'

export default function SignupPage() {
  const [formData, setFormData] = useState({
    email: '',
    name: '',
    nickname: '',
    birth_date: '',
    password: '',
    confirmPassword: '',
  })
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const navigate = useNavigate()
  const { signin } = useAuth()

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    setFormData((prev) => ({ ...prev, [name]: value }))
  }

  // Password validation: at least 8 chars, contains letter, number, special char
  const validatePassword = (pwd: string): string | null => {
    if (pwd.length < 8) {
      return '비밀번호는 8자 이상이어야 합니다.'
    }
    if (!/[a-zA-Z]/.test(pwd)) {
      return '비밀번호는 영문을 포함해야 합니다.'
    }
    if (!/\d/.test(pwd)) {
      return '비밀번호는 숫자를 포함해야 합니다.'
    }
    if (!/[!@#$%^&*()_+\-=\[\]{};:'",.<>?]/.test(pwd)) {
      return '비밀번호는 특수문자를 포함해야 합니다.'
    }
    return null
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    // Client-side validation
    if (!formData.email.trim()) {
      setError('이메일을 입력해주세요.')
      return
    }

    if (!formData.name.trim()) {
      setError('이름을 입력해주세요.')
      return
    }

    if (formData.nickname.length < 2 || formData.nickname.length > 20) {
      setError('닉네임은 2~20자여야 합니다.')
      return
    }

    if (!formData.birth_date) {
      setError('생년월일을 입력해주세요.')
      return
    }

    const passwordError = validatePassword(formData.password)
    if (passwordError) {
      setError(passwordError)
      return
    }

    if (formData.password !== formData.confirmPassword) {
      setError('비밀번호가 일치하지 않습니다.')
      return
    }

    setIsLoading(true)
    try {
      const response = await fetch(
        `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}/api/v1/auth/register`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          credentials: 'include',
          body: JSON.stringify({
            email: formData.email,
            name: formData.name,
            nickname: formData.nickname,
            birth_date: formData.birth_date,
            password: formData.password,
          }),
        }
      )

      const data = await response.json()

      if (data.success) {
        // Register successful, now auto-signin with same credentials
        try {
          await signin(formData.email, formData.password)
          // signin will load user and redirect via authContext
        } catch (signinErr: any) {
          // If auto-signin fails, redirect to signin page
          navigate('/signin')
        }
      } else {
        setError(data.error?.message || '회원가입에 실패했습니다.')
      }
    } catch (err: any) {
      setError('회원가입에 실패했습니다.')
    } finally {
      setIsLoading(false)
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
            name="birth_date"
            placeholder="Birth Date"
            value={formData.birth_date}
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
            disabled={isLoading}
            className="w-full px-4 py-2 bg-bg-button-primary border-2 border-border-cyan-dark text-text-primary font-display hover:bg-opacity-80 disabled:opacity-50"
          >
            {isLoading ? 'Signing Up...' : 'Sign Up'}
          </button>
        </form>
        <p className="text-text-muted text-center mt-4">
          Already have account? <Link to="/signin" className="text-border-cyan-light hover:underline">Sign in</Link>
        </p>
      </div>
    </div>
  )
}
