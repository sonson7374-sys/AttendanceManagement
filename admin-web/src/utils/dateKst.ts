// Date.toISOString()은 항상 UTC로 변환한 날짜를 반환하므로, "오늘" 같은 날짜만 필요한
// 곳에서 그대로 쓰면 한국시간(KST) 00:00~08:59 사이에는 하루 전 날짜가 나온다 — 예를 들어
// 근태조회 일별 탭이 KST 오전에 "오늘" 기본값을 이 방식으로 계산하면, 방금 출근 체크인해
// 실제로는 오늘(KST) 날짜로 저장된 근태기록을 하루 전 날짜로 조회해 못 찾는 문제가 생긴다.
// CLAUDE.md 규칙대로 사용자 표시·근무일 판정은 항상 Asia/Seoul 기준이어야 하므로, 브라우저의
// 로컬 타임존 설정과 무관하게 항상 KST로 날짜를 계산하는 이 유틸을 쓴다.
const KST_TIME_ZONE = 'Asia/Seoul'

// en-CA 로케일의 기본 날짜 포맷이 'YYYY-MM-DD'라는 점을 이용한다.
export function toKstDateString(date: Date): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: KST_TIME_ZONE }).format(date)
}

export function todayKst(): string {
  return toKstDateString(new Date())
}
