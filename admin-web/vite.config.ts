import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'path'

export default defineConfig({
  plugins: [react()],
  // 저장소 최상위 .env(VITE_MAP_API_KEY 등)를 이 프로젝트의 환경변수로 사용한다.
  // admin-web 자체에는 .env 파일을 두지 않고 루트의 .env를 단일 소스로 사용.
  envDir: resolve(__dirname, '..'),
  resolve: {
    alias: {
      '@': resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
