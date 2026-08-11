import { create } from 'zustand'

// 로고 이미지를 다시 받아와야 할 시점(버전)만 기록한다 — 로그인 여부와 무관하게 세션 동안만 유지하면
// 되므로 authStore와 달리 persist하지 않는다. 업로드 성공 시 bump()를 호출하면, 같은 화면에 계속
// 떠 있는 Layout.tsx 사이드바 로고도 새로고침 없이 즉시 최신 이미지로 바뀐다.
interface LogoState {
  version: number
  bump: () => void
}

export const useLogoStore = create<LogoState>()((set) => ({
  version: Date.now(),
  bump: () => set({ version: Date.now() }),
}))
