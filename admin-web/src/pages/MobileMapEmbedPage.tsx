import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import KakaoMap, { geocodeAddress } from '@/components/KakaoMap'

declare global {
  interface Window {
    FlutterBridge?: { postMessage: (message: string) => void }
    geocodeAddress?: (address: string) => void
    setRadius?: (radiusMeters: number) => void
    setPosition?: (latitude: number, longitude: number) => void
    setDevicePosition?: (latitude: number, longitude: number) => void
  }
}

// 모바일 앱 WebView가 로드하는, 로그인/사이드바 없는 순수 지도 페이지. 관리자웹 근무지관리
// 화면과 동일한 KakaoMap 컴포넌트·geocodeAddress()를 그대로 재사용해 지도 로직을 이중 구현하지
// 않는다. 좌표·반경 데이터는 서버에서 가져오지 않고 쿼리파라미터로만 받으므로 인증이 필요 없다.
export default function MobileMapEmbedPage() {
  console.error('[MobileMapEmbedPage] rendering, href=', window.location.href)
  const [params] = useSearchParams()
  const editable = params.get('editable') === 'true'
  const [lat, setLat] = useState(Number(params.get('lat')) || 37.5665)
  const [lng, setLng] = useState(Number(params.get('lng')) || 126.9780)
  const [radius, setRadius] = useState(Number(params.get('radius')) || 100)
  const [height, setHeight] = useState(window.innerHeight)
  // 근무지(위 lat/lng)와는 별개인 "내 위치" — 출퇴근 화면의 실시간 GPS 표시용. 쿼리파라미터에
  // 없으면(근무지관리 화면처럼 내 위치가 필요 없는 경우) undefined로 두어 파란 점을 그리지 않는다.
  const [myLat, setMyLat] = useState<number | undefined>(
    params.has('mylat') ? Number(params.get('mylat')) : undefined,
  )
  const [myLng, setMyLng] = useState<number | undefined>(
    params.has('mylng') ? Number(params.get('mylng')) : undefined,
  )

  useEffect(() => {
    const onResize = () => setHeight(window.innerHeight)
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  // Flutter WebViewController.runJavaScript(...)로 호출할 전역 함수들.
  useEffect(() => {
    window.setRadius = (radiusMeters: number) => setRadius(radiusMeters)
    window.setPosition = (latitude: number, longitude: number) => { setLat(latitude); setLng(longitude) }
    window.setDevicePosition = (latitude: number, longitude: number) => { setMyLat(latitude); setMyLng(longitude) }
    window.geocodeAddress = (address: string) => {
      geocodeAddress(address)
        .then((result) => {
          setLat(result.latitude)
          setLng(result.longitude)
          window.FlutterBridge?.postMessage(JSON.stringify({
            type: 'geocodeResult',
            latitude: result.latitude,
            longitude: result.longitude,
            addressName: result.addressName,
          }))
        })
        .catch((e: Error) => {
          window.FlutterBridge?.postMessage(JSON.stringify({ type: 'geocodeError', message: e.message }))
        })
    }
    return () => {
      delete window.setRadius
      delete window.setPosition
      delete window.setDevicePosition
      delete window.geocodeAddress
    }
  }, [])

  return (
    <KakaoMap
      latitude={lat}
      longitude={lng}
      radiusMeters={radius}
      height={height}
      editable={editable}
      myLatitude={myLat}
      myLongitude={myLng}
      onPositionChange={(latitude, longitude) => {
        setLat(latitude)
        setLng(longitude)
        window.FlutterBridge?.postMessage(JSON.stringify({ type: 'position', latitude, longitude }))
      }}
    />
  )
}
