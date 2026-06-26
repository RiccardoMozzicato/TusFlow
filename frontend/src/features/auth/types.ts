// Mirrors g11.apigateway.DTO.UserInfo returned by GET /api/auth/userinfo.
export interface UserInfo {
  uuid: string
  username: string
  email: string
  fullName: string
  department: string
  organization: string
  roles: string[]
  photoProfileUrl: string | null
}

export type AuthStatus = 'idle' | 'loading' | 'authenticated' | 'anonymous'

export interface AuthState {
  status: AuthStatus
  user: UserInfo | null
  error: string | null
}
