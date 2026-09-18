import { post, get } from './request'

export interface LoginResponse {
  token: string | null
  refreshToken: string | null
  expiresIn: number
  userId: number
  username: string
  nickname: string
  role: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  nickname?: string
}

export function login(username: string, password: string) {
  return post<LoginResponse>('/api/auth/login', { username, password } as LoginRequest)
}

export function register(username: string, password: string, nickname?: string) {
  return post<LoginResponse>('/api/auth/register', { username, password, nickname } as RegisterRequest)
}

export function refresh(refreshToken: string) {
  return post<LoginResponse>(`/api/auth/refresh?refreshToken=${encodeURIComponent(refreshToken)}`)
}

export function me() {
  return get<LoginResponse>('/api/auth/me')
}
