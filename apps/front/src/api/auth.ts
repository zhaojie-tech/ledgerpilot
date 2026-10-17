import request from './request'

export interface LoginForm {
  username: string
  password: string
  rememberMe?: boolean
}

export interface RegisterForm {
  username: string
  password: string
  companyName: string
  taxpayerType: 'GENERAL' | 'SMALL_SCALE'
  industry?: string
}

export interface LoginResponse {
  token: string
  expiresIn: number
  user: {
    userId: number
    tenantId: number
    username: string
    role: string
  }
}

export const authApi = {
  login(data: LoginForm) {
    return request.post<LoginResponse>('/auth/login', data)
  },
  register(data: RegisterForm) {
    return request.post<LoginResponse>('/auth/register', data)
  },
  refresh(token: string) {
    return request.post<LoginResponse>('/auth/refresh', null, {
      headers: { Authorization: `Bearer ${token}` },
    })
  },
  getCurrentUser() {
    return request.get<LoginResponse['user']>('/auth/me')
  },
}
