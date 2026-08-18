import { create } from 'zustand'
import type { LogoType } from '@/api/logo'

// 로고 이미지를 다시 받아와야 할 시점(버전)만 타입별로 독립 기록한다 — 로그인 여부와 무관하게
// 세션 동안만 유지하면 되므로 authStore와 달리 persist하지 않는다. 업로드 성공 시 bump(type)를
// 호출하면, 같은 화면에 계속 떠 있는 해당 타입의 로고도 새로고침 없이 즉시 최신 이미지로 바뀐다.
interface LogoState {
  version: Record<LogoType, number>
  bump: (type: LogoType) => void
}

export const useLogoStore = create<LogoState>()((set) => ({
  version: { login: Date.now(), sidebar: Date.now() },
  bump: (type) => set((state) => ({ version: { ...state.version, [type]: Date.now() } })),
}))
