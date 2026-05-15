import axios from 'axios'

interface AuthResponse {
  accessToken: string
  refreshToken: string
  accessTokenExpiresInSeconds: number
  tokenType: string
  user: { id: number; email: string; fullName: string; role: string }
}

const baseClient = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
})

export const authApi = {
  login: (email: string, password: string) =>
    baseClient.post<AuthResponse>('/auth/login', { email, password }).then((r) => r.data),
  register: (email: string, password: string, fullName: string) =>
    baseClient.post<AuthResponse>('/auth/register', { email, password, fullName }).then((r) => r.data),
  logout: (refreshToken: string) =>
    baseClient.post('/auth/logout', { refreshToken }),
}
