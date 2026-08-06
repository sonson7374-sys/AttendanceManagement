import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { UserRole } from '@/types'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  userId: number | null
  email: string | null
  name: string | null
  role: UserRole | null
  level: string | null
  isAuthenticated: boolean
  login: (payload: { accessToken: string; refreshToken: string; userId: number; email: string; name: string; role: UserRole; level: string }) => void
  logout: () => void
  setTokens: (accessToken: string, refreshToken: string) => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      userId: null,
      email: null,
      name: null,
      role: null,
      level: null,
      isAuthenticated: false,
      login: (payload) =>
        set({
          accessToken: payload.accessToken,
          refreshToken: payload.refreshToken,
          userId: payload.userId,
          email: payload.email,
          name: payload.name,
          role: payload.role,
          level: payload.level,
          isAuthenticated: true,
        }),
      logout: () =>
        set({
          accessToken: null,
          refreshToken: null,
          userId: null,
          email: null,
          name: null,
          role: null,
          level: null,
          isAuthenticated: false,
        }),
      setTokens: (accessToken, refreshToken) => set({ accessToken, refreshToken }),
    }),
    { name: 'auth-storage' }
  )
)
