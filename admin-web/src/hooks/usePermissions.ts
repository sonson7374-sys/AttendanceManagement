import { useQuery } from '@tanstack/react-query'
import { getMyMenuPermissions } from '@/api/menuPermissions'
import { useAuthStore } from '@/store/authStore'

// 권한관리 > 메뉴관리에서 역할별로 끈 (menuKey, actionKey) 조합만 예외로 내려온다.
// 목록에 없는 조합은 기본값 true(표시/활성화)로 취급한다.
export function usePermissions() {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated)

  const { data: overrides = [], isLoading } = useQuery({
    queryKey: ['menu-permissions', 'my'],
    queryFn: () => getMyMenuPermissions().then(r => r.data.data),
    enabled: isAuthenticated,
    staleTime: 60_000,
  })

  const overrideMap = new Map(overrides.map(o => [`${o.menuKey}:${o.actionKey}`, o.enabled]))

  const isMenuVisible = (menuKey: string) => overrideMap.get(`${menuKey}:MENU`) ?? true
  const isActionEnabled = (menuKey: string, actionKey: string) => overrideMap.get(`${menuKey}:${actionKey}`) ?? true

  // 권한 조회가 끝나기 전(로그인 직후 등)에는 isMenuVisible이 기본값 true를 낙관적으로 반환하므로,
  // 대시보드 진입 가드처럼 "권한 없음"을 신뢰해야 하는 판단에는 반드시 isLoading을 함께 확인해야 한다.
  return { isMenuVisible, isActionEnabled, isLoading: isAuthenticated && isLoading }
}
