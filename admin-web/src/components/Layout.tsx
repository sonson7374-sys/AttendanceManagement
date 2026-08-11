import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import { useLogoStore } from '@/store/logoStore'
import * as authApi from '@/api/auth'
import { getUser } from '@/api/users'
import { getOrganizations } from '@/api/organizations'
import { getMyCompany } from '@/api/companies'
import { getLogoUrl } from '@/api/logo'
import { getAttendanceScopeInfo } from '@/api/admin'
import { usePermissions } from '@/hooks/usePermissions'
import { useLevelOptions } from '@/hooks/useLevelOptions'
import toast from 'react-hot-toast'
import uracleLogo from '@/assets/uracle-logo.png'

const NAV_ITEMS = [
  { path: '/', label: '대시보드', icon: '📊', menuKey: 'dashboard' },
  { path: '/my-attendance', label: '출근부', icon: '🕒', menuKey: 'my-attendance' },
  { path: '/attendance', label: '근태 조회', icon: '📋', menuKey: 'attendance' },
  { path: '/approvals', label: '승인함', icon: '✅', menuKey: 'approvals' },
  { path: '/schedules', label: '일정관리', icon: '📅', menuKey: 'schedules' },
  { path: '/employees', label: '직원 관리', icon: '👥', menuKey: 'employees' },
  { path: '/organizations', label: '부서 관리', icon: '🏢', menuKey: 'organizations' },
  { path: '/workplaces', label: '근무지 관리', icon: '📍', menuKey: 'workplaces' },
  { path: '/work-schedules', label: '근무제 관리', icon: '⏰', menuKey: 'work-schedules' },
  { path: '/holidays', label: '휴일/휴가 관리', icon: '🗓️', menuKey: 'holidays' },
  { path: '/permissions', label: '권한관리', icon: '🔐', menuKey: 'permissions' },
  { path: '/audit-logs', label: '감사 로그', icon: '🔍', menuKey: 'audit-logs' },
]

export default function Layout({ children }: { children: React.ReactNode }) {
  const { userId, name, level, accessToken, logout } = useAuthStore()
  const logoVersion = useLogoStore((s) => s.version)
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { isMenuVisible } = usePermissions()
  const levelOptions = useLevelOptions()
  const levelLabel: Record<string, string> = Object.fromEntries(levelOptions.map(o => [o.value, o.label]))

  const { data: me } = useQuery({
    queryKey: ['users', userId],
    queryFn: () => getUser(userId!).then(r => r.data.data),
    enabled: !!userId,
    staleTime: 5 * 60 * 1000,
  })
  const { data: organizations = [] } = useQuery({
    queryKey: ['organizations'],
    queryFn: () => getOrganizations().then(r => r.data.data),
    staleTime: 5 * 60 * 1000,
  })
  const orgName = organizations.find(o => o.id === me?.organizationId)?.name

  const { data: myCompany } = useQuery({
    queryKey: ['companies', 'me'],
    queryFn: () => getMyCompany().then(r => r.data.data),
    staleTime: 5 * 60 * 1000,
  })

  // 직원 관리 메뉴명: 파트장 이상 레벨이면서 관리할 소속 직원이 실제로 있을 때만 "직원 관리",
  // 그 외(직원 레벨이거나 소속 직원이 없는 파트장 이상)는 본인 정보만 다루는 화면이라 "내정보 관리"로 표시한다.
  const { data: scopeInfo } = useQuery({
    queryKey: ['attendance-scope-info'],
    queryFn: () => getAttendanceScopeInfo().then(r => r.data.data),
    staleTime: 5 * 60 * 1000,
  })
  const employeesMenuLabel = scopeInfo?.hasSubordinates ? '직원 관리' : '내정보 관리'

  const handleLogout = async () => {
    try {
      if (accessToken) await authApi.logout(accessToken)
    } finally {
      logout()
      // 로그아웃 시점에 캐시를 비우지 않으면, 다음 로그인한 사용자가 (staleTime 이내에) 이전
      // 사용자의 메뉴 권한 등 캐시된 조회 결과를 그대로 이어받아 잘못된 화면을 보게 된다.
      queryClient.clear()
      navigate('/login')
    }
  }

  return (
    <div style={{ display: 'flex', height: '100vh', background: '#f5f7fa' }}>
      {/* Sidebar */}
      <aside style={{
        width: 220, background: '#1e293b', color: '#fff',
        display: 'flex', flexDirection: 'column',
        flexShrink: 0,
      }}>
        <div style={{ padding: '24px 20px 16px', borderBottom: '1px solid #334155' }}>
          <div style={{ fontWeight: 700, fontSize: 16, color: '#f1f5f9' }}>근태 관리 시스템</div>
          <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 4 }}>관리자 포털</div>
        </div>
        <nav style={{ flex: 1, padding: '12px 0' }}>
          {NAV_ITEMS.filter(item => isMenuVisible(item.menuKey)).map((item) => {
            const active = location.pathname === item.path ||
              (item.path !== '/' && location.pathname.startsWith(item.path))
            return (
              <Link key={item.path} to={item.path} style={{
                display: 'flex', alignItems: 'center', gap: 10,
                padding: '10px 20px', textDecoration: 'none',
                color: active ? '#fff' : '#94a3b8',
                background: active ? '#334155' : 'transparent',
                fontSize: 14,
                transition: 'all 0.15s',
              }}>
                <span>{item.icon}</span>
                <span>{item.menuKey === 'employees' ? employeesMenuLabel : item.label}</span>
              </Link>
            )
          })}
        </nav>
        <div style={{ padding: '16px 20px', borderTop: '1px solid #334155' }}>
          {myCompany && (
            <div style={{ fontSize: 11, color: '#64748b', marginBottom: 4, fontWeight: 600 }}>{myCompany.name}</div>
          )}
          <div style={{ fontSize: 13, color: '#f1f5f9', marginBottom: 4 }}>{name}</div>
          <div style={{ fontSize: 11, color: '#94a3b8', marginBottom: 12 }}>
            {level ? (levelLabel[level] ?? level) : ''}{orgName ? ` · ${orgName}` : ''}
          </div>
          <button onClick={handleLogout} style={{
            width: '100%', padding: '6px 0', background: '#dc2626', color: '#fff',
            border: 'none', borderRadius: 6, cursor: 'pointer', fontSize: 13,
          }}>
            로그아웃
          </button>
        </div>
        <div style={{ padding: '14px 20px', display: 'flex', justifyContent: 'center', background: '#fff' }}>
          <img
            src={getLogoUrl(logoVersion)}
            alt="로고"
            style={{ height: 20 }}
            onError={e => { e.currentTarget.onerror = null; e.currentTarget.src = uracleLogo }}
          />
        </div>
      </aside>

      {/* Main content */}
      <main style={{ flex: 1, overflow: 'auto', padding: 24 }}>
        {children}
      </main>
    </div>
  )
}
