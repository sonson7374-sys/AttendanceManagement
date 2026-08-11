import type { Organization } from '@/types'

// 부서 선택 드롭다운에서 이름만으로는 같은 이름의 하위조직을 구분하기 어려우므로
// 상위부서명을 앞에 붙여 보여준다(예: "DT운영본부 > 미디어운영팀").
export function orgOptionLabel(org: Organization, orgsById: Map<number, Organization>): string {
  const parent = org.parentId != null ? orgsById.get(org.parentId) : undefined
  return parent ? `${parent.name} > ${org.name}` : org.name
}

export function buildOrgsById(organizations: Organization[]): Map<number, Organization> {
  return new Map(organizations.map((o) => [o.id, o]))
}

// 부서 목록을 "상위부서 다음에 그 소속 하위부서들"이 바로 이어지는 트리 순서로 정렬한다
// (같은 레벨끼리는 displayOrder, 없으면 이름 순으로 정렬).
export function sortOrgsHierarchically(organizations: Organization[]): Organization[] {
  const ids = new Set(organizations.map((o) => o.id))
  const byParent = new Map<number | null, Organization[]>()
  for (const org of organizations) {
    // 상위부서가 목록에 없는 경우(비활성 처리 등)에는 최상위로 취급해 누락되지 않게 한다.
    const key = org.parentId != null && ids.has(org.parentId) ? org.parentId : null
    const siblings = byParent.get(key) ?? []
    siblings.push(org)
    byParent.set(key, siblings)
  }
  for (const siblings of byParent.values()) {
    siblings.sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0) || a.name.localeCompare(b.name))
  }

  const result: Organization[] = []
  const visit = (parentId: number | null) => {
    for (const org of byParent.get(parentId) ?? []) {
      result.push(org)
      visit(org.id)
    }
  }
  visit(null)
  return result
}
