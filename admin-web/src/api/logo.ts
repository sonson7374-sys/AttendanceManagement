import client from './client'

// 바이너리 이미지라 <img src>에 URL 문자열을 직접 넣어 쓴다(axios로 감싸지 않음).
// 버전 쿼리파라미터는 브라우저 캐시를 무시하고 최신 로고를 다시 받아오기 위한 캐시버스팅용이다.
export const getLogoUrl = (version: number) => `/api/v1/logo?v=${version}`

export const uploadLogo = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return client.post<void>('/logo', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
