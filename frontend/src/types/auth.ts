// API Request/Response types for authentication

export interface RegisterRequest {
  email: string
  password: string
  nickname: string
  name: string
  birth_date: string // "yyyy-MM-dd"
}

export interface SigninRequest {
  email: string
  password: string
}

export interface AuthTokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface DefaultBlog {
  id: number
  title: string
  urlSlug: string
  isSetupCompleted: boolean
}

export interface AuthMeResponse {
  id: number
  email: string
  nickname: string
  name: string
  birthDate: string
  role: 'USER' | 'ADMIN'
  status: 'ACTIVE' | 'SUSPENDED'
  profileImageUrl: string | null
  defaultBlog: DefaultBlog
}

export interface AuthUser {
  id: number
  email: string
  nickname: string
  name: string
  birthDate: string
  profileImageUrl: string | null
  role: 'USER' | 'ADMIN'
  status: 'ACTIVE' | 'SUSPENDED'
  defaultBlog: DefaultBlog
}
