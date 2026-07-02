import { useState, useEffect } from 'react'
import AppShell from '../components/layout/AppShell'
import { useAuth } from '../lib/authContext'
import { useUserSettings } from '../hooks/useUserSettings'
import type { UserSettingsRequest, ChangePasswordRequest } from '../types/settings'

export default function SettingsProfilePage() {
  const { user } = useAuth()
  const { updateProfile, changePassword } = useUserSettings()
  const [activeTab, setActiveTab] = useState('profile')

  // Profile form
  const [profileData, setProfileData] = useState<UserSettingsRequest>({
    name: '',
    nickname: '',
    bio: '',
    birthDate: '',
    profileImageUrl: '',
  })
  const [profileLoading, setProfileLoading] = useState(false)
  const [profileSuccess, setProfileSuccess] = useState(false)
  const [profileError, setProfileError] = useState('')

  // Password form
  const [passwordData, setPasswordData] = useState<ChangePasswordRequest>({
    currentPassword: '',
    newPassword: '',
  })
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [passwordLoading, setPasswordLoading] = useState(false)
  const [passwordSuccess, setPasswordSuccess] = useState(false)
  const [passwordError, setPasswordError] = useState('')

  // Initialize profile form with user data
  useEffect(() => {
    if (user) {
      setProfileData({
        name: user.name || '',
        nickname: user.nickname || '',
        bio: '',
        birthDate: user.birthDate || '',
        profileImageUrl: user.profileImageUrl || '',
      })
    }
  }, [user])

  const handleProfileChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target
    setProfileData((prev) => ({ ...prev, [name]: value }))
  }

  const handleProfileSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setProfileError('')
    setProfileSuccess(false)
    setProfileLoading(true)

    try {
      const result = await updateProfile(profileData)
      if (result) {
        setProfileSuccess(true)
        setTimeout(() => setProfileSuccess(false), 3000)
      } else {
        setProfileError('프로필 수정에 실패했습니다.')
      }
    } catch (err: any) {
      setProfileError(err.message || '프로필 수정에 실패했습니다.')
    } finally {
      setProfileLoading(false)
    }
  }

  const handlePasswordChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    if (name === 'confirm') {
      setPasswordConfirm(value)
    } else {
      setPasswordData((prev) => ({ ...prev, [name]: value }))
    }
  }

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setPasswordError('')
    setPasswordSuccess(false)

    // Validation
    if (passwordData.newPassword !== passwordConfirm) {
      setPasswordError('새 비밀번호가 일치하지 않습니다.')
      return
    }

    if (passwordData.newPassword.length < 8) {
      setPasswordError('비밀번호는 8자 이상이어야 합니다.')
      return
    }

    if (!/[a-zA-Z]/.test(passwordData.newPassword)) {
      setPasswordError('비밀번호는 영문을 포함해야 합니다.')
      return
    }

    if (!/\d/.test(passwordData.newPassword)) {
      setPasswordError('비밀번호는 숫자를 포함해야 합니다.')
      return
    }

    if (!/[!@#$%^&*()_+\-=\[\]{};:'",.<>?]/.test(passwordData.newPassword)) {
      setPasswordError('비밀번호는 특수문자를 포함해야 합니다.')
      return
    }

    setPasswordLoading(true)
    try {
      const result = await changePassword(passwordData)
      if (result) {
        setPasswordSuccess(true)
        setPasswordData({ currentPassword: '', newPassword: '' })
        setPasswordConfirm('')
        setTimeout(() => setPasswordSuccess(false), 3000)
      } else {
        setPasswordError('비밀번호 변경에 실패했습니다.')
      }
    } catch (err: any) {
      setPasswordError(err.message || '비밀번호 변경에 실패했습니다.')
    } finally {
      setPasswordLoading(false)
    }
  }

  return (
    <AppShell showSidebar={true}>
      <div className="p-6">
        <h1 className="font-display text-3xl text-text-primary mb-6">Settings</h1>

        {/* Tabs */}
        <div className="flex gap-4 mb-6 border-b-2 border-border-purple-dark">
          <button
            onClick={() => setActiveTab('profile')}
            className={`px-4 py-2 font-display transition ${
              activeTab === 'profile'
                ? 'border-b-4 border-border-cyan-dark text-text-primary'
                : 'text-text-muted hover:text-text-primary'
            }`}
          >
            Profile
          </button>
          <button
            onClick={() => setActiveTab('password')}
            className={`px-4 py-2 font-display transition ${
              activeTab === 'password'
                ? 'border-b-4 border-border-cyan-dark text-text-primary'
                : 'text-text-muted hover:text-text-primary'
            }`}
          >
            Security
          </button>
        </div>

        {/* Profile Tab */}
        {activeTab === 'profile' && (
          <div className="bg-bg-panel border-2 border-border-cyan-dark p-6 shadow-card max-w-2xl">
            <h2 className="font-display text-text-primary mb-6">User Profile</h2>
            <form onSubmit={handleProfileSubmit} className="space-y-4">
              <div>
                <label className="block text-text-primary mb-2 text-sm">Email (read-only)</label>
                <input
                  type="email"
                  value={user?.email || ''}
                  disabled
                  className="w-full px-4 py-2 bg-bg-input border-2 border-border-purple-dark text-text-muted disabled:opacity-50"
                />
              </div>
              <div>
                <label className="block text-text-primary mb-2 text-sm">Nickname</label>
                <input
                  type="text"
                  name="nickname"
                  value={profileData.nickname}
                  onChange={handleProfileChange}
                  disabled={profileLoading}
                  className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary disabled:opacity-50"
                />
              </div>
              <div>
                <label className="block text-text-primary mb-2 text-sm">Name</label>
                <input
                  type="text"
                  name="name"
                  value={profileData.name}
                  onChange={handleProfileChange}
                  disabled={profileLoading}
                  className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary disabled:opacity-50"
                />
              </div>
              <div>
                <label className="block text-text-primary mb-2 text-sm">Birth Date</label>
                <input
                  type="date"
                  name="birthDate"
                  value={profileData.birthDate}
                  onChange={handleProfileChange}
                  disabled={profileLoading}
                  className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary disabled:opacity-50"
                />
              </div>
              <div>
                <label className="block text-text-primary mb-2 text-sm">Bio</label>
                <textarea
                  name="bio"
                  value={profileData.bio}
                  onChange={handleProfileChange}
                  disabled={profileLoading}
                  className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary disabled:opacity-50"
                  rows={3}
                />
              </div>
              <div>
                <label className="block text-text-primary mb-2 text-sm">Profile Image URL</label>
                <input
                  type="text"
                  name="profileImageUrl"
                  value={profileData.profileImageUrl}
                  onChange={handleProfileChange}
                  placeholder="https://example.com/image.jpg"
                  disabled={profileLoading}
                  className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary disabled:opacity-50"
                />
              </div>

              {profileSuccess && (
                <div className="bg-green-900 bg-opacity-20 border-2 border-green-500 p-3 text-green-400 text-sm">
                  ✓ 프로필이 수정되었습니다.
                </div>
              )}

              {profileError && (
                <div className="bg-border-danger bg-opacity-10 border-2 border-border-danger p-3 text-border-danger text-sm">
                  {profileError}
                </div>
              )}

              <button
                type="submit"
                disabled={profileLoading}
                className="px-6 py-2 bg-bg-button-primary border-2 border-border-cyan-dark text-text-primary font-display hover:bg-opacity-80 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {profileLoading ? 'Saving...' : 'Save'}
              </button>
            </form>
          </div>
        )}

        {/* Security Tab */}
        {activeTab === 'password' && (
          <div className="bg-bg-panel border-2 border-border-cyan-dark p-6 shadow-card max-w-2xl">
            <h2 className="font-display text-text-primary mb-6">Change Password</h2>
            <form onSubmit={handlePasswordSubmit} className="space-y-4">
              <div>
                <label className="block text-text-primary mb-2 text-sm">Current Password</label>
                <input
                  type="password"
                  name="currentPassword"
                  value={passwordData.currentPassword}
                  onChange={handlePasswordChange}
                  disabled={passwordLoading}
                  className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary disabled:opacity-50"
                />
              </div>
              <div>
                <label className="block text-text-primary mb-2 text-sm">New Password</label>
                <input
                  type="password"
                  name="newPassword"
                  value={passwordData.newPassword}
                  onChange={handlePasswordChange}
                  disabled={passwordLoading}
                  className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary disabled:opacity-50"
                />
                <p className="text-text-muted text-xs mt-1">
                  8자 이상, 영문·숫자·특수문자 포함
                </p>
              </div>
              <div>
                <label className="block text-text-primary mb-2 text-sm">Confirm Password</label>
                <input
                  type="password"
                  name="confirm"
                  value={passwordConfirm}
                  onChange={handlePasswordChange}
                  disabled={passwordLoading}
                  className="w-full px-4 py-2 bg-bg-input border-2 border-border-cyan-dark text-text-primary disabled:opacity-50"
                />
              </div>

              {passwordSuccess && (
                <div className="bg-green-900 bg-opacity-20 border-2 border-green-500 p-3 text-green-400 text-sm">
                  ✓ 비밀번호가 변경되었습니다.
                </div>
              )}

              {passwordError && (
                <div className="bg-border-danger bg-opacity-10 border-2 border-border-danger p-3 text-border-danger text-sm">
                  {passwordError}
                </div>
              )}

              <button
                type="submit"
                disabled={passwordLoading}
                className="px-6 py-2 bg-bg-button-primary border-2 border-border-cyan-dark text-text-primary font-display hover:bg-opacity-80 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {passwordLoading ? 'Changing...' : 'Change Password'}
              </button>
            </form>
          </div>
        )}
      </div>
    </AppShell>
  )
}
