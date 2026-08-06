/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_MAP_API_KEY?: string
  readonly VITE_MAP_PROVIDER?: 'kakao' | 'naver' | 'google'
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

// Kakao Maps SDK 글로벌 타입
interface Window {
  kakao: {
    maps: {
      load: (callback: () => void) => void
      Map: new (container: HTMLElement, options: object) => KakaoMap
      LatLng: new (lat: number, lng: number) => KakaoLatLng
      Marker: new (options: object) => KakaoMarker
      Circle: new (options: object) => KakaoCircle
      services: {
        Geocoder: new () => KakaoGeocoder
        Status: { OK: string; ZERO_RESULT: string; ERROR: string }
      }
      event: {
        addListener: (
          target: KakaoMarker | KakaoMap,
          type: string,
          handler: (event: { latLng: KakaoLatLng }) => void,
        ) => void
      }
    }
  }
}
interface KakaoMap { setCenter: (latlng: KakaoLatLng) => void }
interface KakaoLatLng { getLat: () => number; getLng: () => number }
interface KakaoMarker {
  setMap: (map: KakaoMap | null) => void
  getPosition: () => KakaoLatLng
  setPosition: (latlng: KakaoLatLng) => void
}
interface KakaoCircle {
  setMap: (map: KakaoMap | null) => void
  setPosition: (latlng: KakaoLatLng) => void
}
interface KakaoGeocodeResult { address_name: string; x: string; y: string }
interface KakaoGeocoder {
  addressSearch: (
    address: string,
    callback: (result: KakaoGeocodeResult[], status: string) => void,
  ) => void
}
