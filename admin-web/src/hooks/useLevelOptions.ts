import { useQuery } from '@tanstack/react-query'
import { getCommonCodes } from '@/api/commonCodes'

// 권한레벨(직책 성격의 표시값)은 그룹코드 LEVEL_ROLL(사장/부문장/본부장/실장/팀장/파트장/직원 등)에서 불러온다.
// role(UserRole)과 달리 실제 인가에는 쓰이지 않는 값이라 화이트리스트 없이 등록된 코드를 그대로 노출한다.
export interface LevelOption { value: string; label: string }

export function useLevelOptions(): LevelOption[] {
  const { data: levelCodes = [] } = useQuery({
    queryKey: ['common-codes', 'LEVEL_ROLL'],
    queryFn: () => getCommonCodes('LEVEL_ROLL').then(r => r.data.data),
    staleTime: 5 * 60 * 1000,
  })

  return levelCodes
    .filter(c => c.active)
    .sort((a, b) => a.displayOrder - b.displayOrder)
    .map(c => ({ value: c.code, label: c.codeName }))
}
