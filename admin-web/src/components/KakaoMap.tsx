import { useEffect, useRef, useState } from 'react'

interface Props {
  latitude: number
  longitude: number
  radiusMeters: number
  height?: number
  editable?: boolean
  onPositionChange?: (latitude: number, longitude: number) => void
}

const MAP_API_KEY = import.meta.env.VITE_MAP_API_KEY
const HAS_KEY = MAP_API_KEY && MAP_API_KEY !== 'your_kakao_map_javascript_api_key_here'

let kakaoScriptPromise: Promise<void> | null = null

function loadKakaoScript(): Promise<void> {
  if (window.kakao?.maps?.services) return Promise.resolve()
  if (kakaoScriptPromise) return kakaoScriptPromise

  kakaoScriptPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    // services 라이브러리를 포함해야 주소 검색(Geocoder)을 사용할 수 있다.
    script.src = `//dapi.kakao.com/v2/maps/sdk.js?appkey=${MAP_API_KEY}&autoload=false&libraries=services`
    script.onload = () => window.kakao.maps.load(resolve)
    script.onerror = () => {
      kakaoScriptPromise = null
      reject(new Error('카카오맵 스크립트를 불러오지 못했습니다.'))
    }
    document.head.appendChild(script)
  })
  return kakaoScriptPromise
}

/**
 * 주소를 위도/경도로 변환한다 (Kakao Geocoder).
 * 근무지 등록·수정 화면에서 "주소 검색" 시 사용.
 */
export function geocodeAddress(
  address: string,
): Promise<{ latitude: number; longitude: number; addressName: string }> {
  if (!HAS_KEY) {
    return Promise.reject(new Error('VITE_MAP_API_KEY가 설정되지 않았습니다.'))
  }
  return loadKakaoScript().then(
    () =>
      new Promise((resolve, reject) => {
        const geocoder = new window.kakao.maps.services.Geocoder()
        geocoder.addressSearch(address, (result, status) => {
          if (status === window.kakao.maps.services.Status.OK && result[0]) {
            resolve({
              latitude: parseFloat(result[0].y),
              longitude: parseFloat(result[0].x),
              addressName: result[0].address_name,
            })
          } else {
            reject(new Error('주소를 찾을 수 없습니다. 정확한 주소를 입력해주세요.'))
          }
        })
      }),
  )
}

export default function KakaoMap({ latitude, longitude, radiusMeters, height = 200, editable = false, onPositionChange }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    if (!HAS_KEY || !containerRef.current) return
    let mounted = true

    loadKakaoScript()
      .then(() => {
        if (!mounted || !containerRef.current) return
        const { Map, LatLng, Marker, Circle, event } = window.kakao.maps
        const center = new LatLng(latitude, longitude)
        const map = new Map(containerRef.current, { center, level: 3 })
        const marker = new Marker({ map, position: center, draggable: editable })
        marker.setMap(map)
        const circle = new Circle({
          map,
          center,
          radius: radiusMeters,
          strokeWeight: 2,
          strokeColor: '#2563eb',
          strokeOpacity: 0.8,
          fillColor: '#2563eb',
          fillOpacity: 0.1,
        })
        circle.setMap(map)

        if (editable && onPositionChange) {
          event.addListener(marker, 'dragend', () => {
            const pos = marker.getPosition()
            marker.setPosition(pos)
            circle.setPosition(pos)
            onPositionChange(pos.getLat(), pos.getLng())
          })
          event.addListener(map, 'click', (mouseEvent: { latLng: { getLat(): number; getLng(): number } }) => {
            const pos = mouseEvent.latLng
            marker.setPosition(pos)
            circle.setPosition(pos)
            onPositionChange(pos.getLat(), pos.getLng())
          })
        }
      })
      .catch(() => setError(true))

    return () => { mounted = false }
  }, [latitude, longitude, radiusMeters, editable])

  if (!HAS_KEY) {
    return (
      <div style={{
        height, background: '#f1f5f9', borderRadius: 8,
        display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center', gap: 6,
      }}>
        <span style={{ fontSize: 28 }}>🗺️</span>
        <span style={{ fontSize: 12, color: '#64748b', textAlign: 'center' }}>
          {latitude.toFixed(6)}, {longitude.toFixed(6)}
        </span>
        <span style={{ fontSize: 11, color: '#94a3b8' }}>반경 {radiusMeters}m</span>
        <span style={{ fontSize: 10, color: '#cbd5e1' }}>VITE_MAP_API_KEY 미설정</span>
      </div>
    )
  }

  if (error) {
    return (
      <div style={{
        height, background: '#fef2f2', borderRadius: 8,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <span style={{ fontSize: 13, color: '#ef4444' }}>지도 로드 실패</span>
      </div>
    )
  }

  return <div ref={containerRef} style={{ height, borderRadius: 8, overflow: 'hidden' }} />
}
