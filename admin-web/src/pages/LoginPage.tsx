import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import { useLogoStore } from '@/store/logoStore'
import { getLogoUrl } from '@/api/logo'
import * as authApi from '@/api/auth'
import toast from 'react-hot-toast'
import uracleLogo from '@/assets/uracle-logo.png'

export default function LoginPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const login = useAuthStore((s) => s.login)
  const logoVersion = useLogoStore((s) => s.version.login)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    try {
      const { data } = await authApi.login(email, password)
      // 로그아웃 절차를 거치지 않고(토큰 만료 리다이렉트 등) 로그인 화면에 온 경우까지 대비해
      // 로그인 성공 시에도 캐시를 비워, 이전 사용자의 조회 결과가 섞여 보이는 일이 없도록 한다.
      queryClient.clear()
      login(data.data)
      navigate('/')
    } catch (err: any) {
      toast.error(err?.response?.data?.message ?? '로그인 실패')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center',
      justifyContent: 'center', background: '#f5f7fa',
    }}>
      <div style={{
        background: '#fff', padding: 40, borderRadius: 12,
        boxShadow: '0 4px 24px rgba(0,0,0,0.08)', width: 380,
      }}>
        <img
          src={getLogoUrl('login', logoVersion)}
          alt="로고"
          style={{ height: 32, marginBottom: 20, display: 'block' }}
          onError={e => { e.currentTarget.onerror = null; e.currentTarget.src = uracleLogo }}
        />
        <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 8, color: '#1e293b' }}>
          근태 관리 시스템
        </h1>
        <p style={{ fontSize: 14, color: '#64748b', marginBottom: 28 }}>관리자 포털에 로그인하세요.</p>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div>
            <label style={labelStyle}>이메일</label>
            <input
              type="email" value={email} onChange={(e) => setEmail(e.target.value)}
              required style={inputStyle} placeholder="admin@attendance.local"
            />
          </div>
          <div>
            <label style={labelStyle}>비밀번호</label>
            <input
              type="password" value={password} onChange={(e) => setPassword(e.target.value)}
              required style={inputStyle} placeholder="••••••••"
            />
          </div>
          <button type="submit" disabled={loading} style={{
            padding: '12px 0', background: '#2563eb', color: '#fff',
            border: 'none', borderRadius: 8, cursor: loading ? 'not-allowed' : 'pointer',
            fontWeight: 600, fontSize: 15, opacity: loading ? 0.7 : 1, marginTop: 8,
          }}>
            {loading ? '로그인 중...' : '로그인'}
          </button>
        </form>
      </div>
    </div>
  )
}

const labelStyle: React.CSSProperties = {
  display: 'block', fontSize: 13, fontWeight: 500, color: '#374151', marginBottom: 6,
}
const inputStyle: React.CSSProperties = {
  width: '100%', padding: '10px 12px', border: '1px solid #d1d5db',
  borderRadius: 8, fontSize: 14, outline: 'none',
}
