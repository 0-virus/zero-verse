// Settings API types

export interface UserSettingsResponse {
  id: number
  email: string
  nickname: string
  name: string
  bio?: string
  birthDate: string
  profileImageUrl?: string
  role: 'USER' | 'ADMIN'
  status: 'ACTIVE' | 'SUSPENDED'
  createdAt: string
  updatedAt: string
}

export interface UserSettingsRequest {
  name?: string
  nickname?: string
  bio?: string
  birthDate?: string
  profileImageUrl?: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface BlogSettingsResponse {
  id: number
  title: string
  urlSlug: string
  description?: string
  isSetupCompleted: boolean
  ownerId: number
  createdAt: string
  updatedAt: string
}

export interface BlogSettingsRequest {
  title?: string
  urlSlug?: string
  description?: string
}

export interface BlogInitialSetupRequest {
  title?: string
  urlSlug?: string
  description?: string
}

export interface OwnerInfo {
  userId: number
  nickname: string
  profileImageUrl?: string
  bio?: string
}

export interface BlogPublicResponse {
  id: number
  title: string
  urlSlug: string
  description?: string
  owner: OwnerInfo
  createdAt: string
}
