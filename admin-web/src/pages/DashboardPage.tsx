import { useQuery } from '@tanstack/react-query'
import { BarChart, Bar, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { getDashboardStats } from '@/api/admin'
import type { DashboardStats } from '@/types'

const CHART_HUE = '#2563eb'
const CHART_GRID = '#e2e8f0'
const CHART_TEXT = '#64748b'

interface StatCardProps {
  label: string
  value: number
  color: string
  icon: string
  description?: string
}

function StatCard({ label, value, color, icon, description }: StatCardProps) {
  return (
    <div style={{
      background: '#fff', borderRadius: 12, padding: '24px',
      boxShadow: '0 1px 6px rgba(0,0,0,0.06)', borderTop: `4px solid ${color}`,
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: 13, color: '#64748b', marginBottom: 8 }}>{label}</p>
          <p style={{ fontSize: 36, fontWeight: 700, color: '#1e293b' }}>{value.toLocaleString()}</p>
          {description && <p style={{ fontSize: 12, color: '#94a3b8', marginTop: 4 }}>{description}</p>}
        </div>
        <span style={{ fontSize: 32 }}>{icon}</span>
      </div>
    </div>
  )
}

export default function DashboardPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => getDashboardStats().then((r) => r.data.data),
    refetchInterval: 60_000,
  })

  const today = new Date().toLocaleDateString('ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric', weekday: 'long',
  })

  return (
    <div>
      <div style={{ marginBottom: 28 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, color: '#1e293b' }}>대시보드</h1>
        <p style={{ fontSize: 14, color: '#64748b', marginTop: 4 }}>{today}</p>
      </div>

      {isLoading && <p style={{ color: '#64748b' }}>로딩 중...</p>}
      {error && <p style={{ color: '#dc2626' }}>데이터를 불러오지 못했습니다.</p>}

      {data && (
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
          gap: 20,
        }}>
          <StatCard
            label="전체 재직자"
            value={data.totalEmployees}
            color="#3b82f6"
            icon="👥"
            description="활성 계정 수"
          />
          <StatCard
            label="오늘 출근"
            value={data.presentToday}
            color="#10b981"
            icon="✅"
          />
          <StatCard
            label="지각"
            value={data.lateToday}
            color="#f59e0b"
            icon="⏰"
          />
          <StatCard
            label="결근"
            value={data.absentToday}
            color="#ef4444"
            icon="❌"
          />
          <StatCard
            label="휴가"
            value={data.onLeaveToday}
            color="#0ea5e9"
            icon="🌴"
          />
          <StatCard
            label="외근·출장"
            value={data.outsideWorkToday}
            color="#14b8a6"
            icon="🚗"
          />
          <StatCard
            label="퇴근"
            value={data.checkedOutToday}
            color="#64748b"
            icon="🏁"
          />
          <StatCard
            label="승인 대기"
            value={data.pendingApprovals}
            color="#8b5cf6"
            icon="📝"
            description="근태 수정 요청"
          />
        </div>
      )}

      {data && (
        <div style={{
          marginTop: 32, background: '#fff', borderRadius: 12, padding: 24,
          boxShadow: '0 1px 6px rgba(0,0,0,0.06)',
        }}>
          <h2 style={{ fontSize: 16, fontWeight: 600, color: '#1e293b', marginBottom: 16 }}>
            오늘 출근 현황
          </h2>
          <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
            <div style={{ flex: 1, height: 12, background: '#e2e8f0', borderRadius: 8, overflow: 'hidden' }}>
              <div style={{
                height: '100%',
                width: `${data.totalEmployees > 0 ? (data.presentToday / data.totalEmployees) * 100 : 0}%`,
                background: '#10b981', borderRadius: 8, transition: 'width 0.5s',
              }} />
            </div>
            <span style={{ fontSize: 13, color: '#64748b', flexShrink: 0 }}>
              {data.totalEmployees > 0
                ? Math.round((data.presentToday / data.totalEmployees) * 100)
                : 0}% 출근
            </span>
          </div>
        </div>
      )}

      {data && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: 20, marginTop: 20 }}>
          <ChartCard title="부서별 출근율">
            {data.departmentAttendanceRates.length === 0 ? (
              <EmptyChartState />
            ) : (
              <ResponsiveContainer width="100%" height={240}>
                <BarChart data={data.departmentAttendanceRates} margin={{ top: 4, right: 8, left: -12, bottom: 4 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID} vertical={false} />
                  <XAxis dataKey="organizationName" tick={{ fontSize: 12, fill: CHART_TEXT }} axisLine={{ stroke: CHART_GRID }} tickLine={false} />
                  <YAxis unit="%" tick={{ fontSize: 12, fill: CHART_TEXT }} axisLine={false} tickLine={false} />
                  <Tooltip formatter={(v) => [`${v}%`, '출근율']} contentStyle={{ fontSize: 13, borderRadius: 8, border: '1px solid #e2e8f0' }} />
                  <Bar dataKey="rate" fill={CHART_HUE} radius={[4, 4, 0, 0]} maxBarSize={40} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </ChartCard>

          <ChartCard title="월별 지각 추이 (최근 6개월)">
            <ResponsiveContainer width="100%" height={240}>
              <LineChart data={data.monthlyLateTrend} margin={{ top: 4, right: 8, left: -12, bottom: 4 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID} vertical={false} />
                <XAxis dataKey="yearMonth" tick={{ fontSize: 12, fill: CHART_TEXT }} axisLine={{ stroke: CHART_GRID }} tickLine={false} />
                <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: CHART_TEXT }} axisLine={false} tickLine={false} />
                <Tooltip formatter={(v) => [`${v}건`, '지각']} contentStyle={{ fontSize: 13, borderRadius: 8, border: '1px solid #e2e8f0' }} />
                <Line type="monotone" dataKey="lateCount" stroke={CHART_HUE} strokeWidth={2} dot={{ r: 4, fill: CHART_HUE }} />
              </LineChart>
            </ResponsiveContainer>
          </ChartCard>
        </div>
      )}
    </div>
  )
}

function ChartCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ background: '#fff', borderRadius: 12, padding: 24, boxShadow: '0 1px 6px rgba(0,0,0,0.06)' }}>
      <h2 style={{ fontSize: 16, fontWeight: 600, color: '#1e293b', marginBottom: 16 }}>{title}</h2>
      {children}
    </div>
  )
}

function EmptyChartState() {
  return (
    <div style={{ height: 240, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#94a3b8', fontSize: 13 }}>
      표시할 데이터가 없습니다.
    </div>
  )
}
