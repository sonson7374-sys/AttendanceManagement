import client from './client'

// 로그인 화면(관리자웹 로그인 페이지 + 모바일 앱 로그인 화면)용과 관리자웹 좌측 메뉴 하단용을
// 서로 독립된 이미지로 관리한다.
export type LogoType = 'login' | 'sidebar'

// 바이너리 이미지라 <img src>에 URL 문자열을 직접 넣어 쓴다(axios로 감싸지 않음).
// 버전 쿼리파라미터는 브라우저 캐시를 무시하고 최신 로고를 다시 받아오기 위한 캐시버스팅용이다.
export const getLogoUrl = (type: LogoType, version: number) => `/api/v1/logo/${type}?v=${version}`

export const uploadLogo = (type: LogoType, file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return client.post<void>(`/logo/${type}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
