import { useEffect, useState } from 'react'

const NARROW_BREAKPOINT_PX = 480

// 모바일 앱 WebView("근무지관리웹뷰")처럼 폰 너비 화면에서 고정폭 모달·3열 그리드가
// 잘리지 않도록, 화면이 좁을 때 레이아웃을 바꿀 수 있게 너비를 구독한다.
export function useIsNarrowViewport(breakpointPx: number = NARROW_BREAKPOINT_PX) {
  const [isNarrow, setIsNarrow] = useState(() => window.innerWidth < breakpointPx)

  useEffect(() => {
    const mediaQuery = window.matchMedia(`(max-width: ${breakpointPx - 1}px)`)
    const handleChange = () => setIsNarrow(mediaQuery.matches)
    handleChange()
    mediaQuery.addEventListener('change', handleChange)
    return () => mediaQuery.removeEventListener('change', handleChange)
  }, [breakpointPx])

  return isNarrow
}
