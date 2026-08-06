import client from './client'
import type { ApiResponse, LoginResponse } from '@/types'

export const login = (email: string, password: string) =>
  client.post<ApiResponse<LoginResponse>>('/auth/login', { email, password })

export const logout = (accessToken: string) =>
  client.post('/auth/logout', { accessToken })
