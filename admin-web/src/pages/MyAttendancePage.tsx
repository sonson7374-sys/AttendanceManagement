import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getMyTodayAttendance, checkInMyAttendance, checkOutMyAttendance,
  getMyAssignedWorkplaces, getMyAttendanceRegister,
} from '@/api/myAttendance'
import type { AttendanceRegisterRow } from '@/api/myAttendance'
import type { AttendanceStatus } from '@/types'
import toast from 'react-hot-toast'

const STATUS_LABEL: Record<AttendanceStatus, string> = {
  BEFORE_WORK: '미출근', WORKING: '근무 중', BREAK: '휴게', FINISHED: '퇴근',
  LATE: '지각', EARLY_LEAVE: '조퇴', ABSENT: '결근', LEAVE: '휴가',
  OUTSIDE_WORK: '외근', BUSINESS_TRIP: '출장', REMOTE_WORK: '재택',
}
const STATUS_COLOR: Record<AttendanceStatus, string> = {
  BEFORE_WORK: '#94a3b8', WORKING: '#10b981', BREAK: '#f59e0b', FINISHED: '#3b82f6',
  LATE: '#f97316', EARLY_LEAVE: '#a855f7', ABSENT: '#ef4444', LEAVE: '#06b6d4',
  OUTSIDE_WORK: '#64748b', BUSINESS_TRIP: '#8b5cf6', REMOTE_WORK: '#0ea5e9',
}
const CAN_CHECK_IN_STATUSES: AttendanceStatus[] = ['BEFORE_WORK', 'ABSENT']
const CAN_CHECK_OUT_STATUSES: AttendanceStatus[] = ['WORKING', 'LATE', 'BREAK']

const DEVICE_ID_KEY = 'web_device_id'
function getWebDeviceId(): string {
  let id = localStorage.getItem(DEVICE_ID_KEY)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(DEVICE_ID_KEY, id)
  }
  return id
}

const fmtMinutes = (m?: number) => {
  if (m == null) return '-'
  const h = Math.floor(m / 60)
  const mm = m % 60
  return `${String(h).padStart(2, '0')}:${String(mm).padStart(2, '0')}`
}
const fmtTimeKst = (iso?: string) =>
  iso ? new Date(iso).toLocaleTimeString('ko-KR', { timeZone: 'Asia/Seoul', hour: '2-digit', minute: '2-digit', hour12: false }) : '-'
const fmtScheduleTime = (t?: string) => (t ? t.slice(0, 5) : '-')
const weekdayLabel = (dateStr: string) => ['일', '월', '화', '수', '목', '금', '토'][new Date(`${dateStr}T00:00:00`).getDay()]

function CheckInOutTab() {
  const qc = useQueryClient()
  const [now, setNow] = useState(new Date())
  const [selectedWorkplaceId, setSelectedWorkplaceId] = useState<number | null>(null)

  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(timer)
  }, [])

  const { data: today, isLoading } = useQuery({
    queryKey: ['my-attendance-today'],
    queryFn: () => getMyTodayAttendance().then(r => r.data.data),
    refetchInterval: 30_000,
  })

  // 브라우저 GPS로 실제 위치를 매번 측위하는 대신, 계정에 배정된 근무지(근무지 관리)의
  // 위도/경도/정확도를 그대로 사용한다 — 웹에서는 물리적 위치와 무관하게 출퇴근이 가능하다.
  const { data: workplaces = [] } = useQuery({
    queryKey: ['my-assigned-workplaces'],
    queryFn: () => getMyAssignedWorkplaces().then(r => r.data.data),
  })

  useEffect(() => {
    if (workplaces.length > 0 && selectedWorkplaceId == null) {
      setSelectedWorkplaceId(workplaces[0].id)
    }
  }, [workplaces, selectedWorkplaceId])

  const selectedWorkplace = workplaces.find(w => w.id === selectedWorkplaceId) ?? workplaces[0]

  const checkAction = useMutation({
    mutationFn: async (action: 'in' | 'out') => {
      if (!selectedWorkplace) throw new Error('배정된 근무지가 없습니다. 관리자에게 문의해주세요.')
      const base = {
        latitude: selectedWorkplace.latitude,
        longitude: selectedWorkplace.longitude,
        accuracyMeters: selectedWorkplace.maxAccuracyMeters ?? 5,
        capturedAt: new Date().toISOString(),
        deviceId: getWebDeviceId(),
        devicePlatform: 'WEB',
        mockLocationDetected: false,
      }
      if (action === 'in') {
        return checkInMyAttendance({ ...base, workplaceId: selectedWorkplace.id })
      }
      return checkOutMyAttendance(base)
    },
    onSuccess: (_res, action) => {
      toast.success(action === 'in' ? '출근 처리되었습니다.' : '퇴근 처리되었습니다.')
      qc.invalidateQueries({ queryKey: ['my-attendance-today'] })
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? e?.message ?? '처리 중 오류가 발생했습니다.'),
  })

  const status = today?.status
  const canCheckIn = !!status && CAN_CHECK_IN_STATUSES.includes(status)
  const canCheckOut = !!status && CAN_CHECK_OUT_STATUSES.includes(status)

  return (
    <div style={{ maxWidth: 560 }}>
      <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', padding: 32, textAlign: 'center' }}>
        <div style={{ fontSize: 13, color: '#64748b', marginBottom: 4 }}>
          {isLoading ? '조회 중...' : status ? STATUS_LABEL[status] : '-'}
        </div>
        <div style={{ fontSize: 48, fontWeight: 700, color: '#1e293b', fontVariantNumeric: 'tabular-nums', marginBottom: 20 }}>
          {now.toLocaleTimeString('ko-KR', { timeZone: 'Asia/Seoul', hour12: false })}
        </div>

        <div style={{ fontSize: 13, color: '#64748b', marginBottom: 20 }}>
          {workplaces.length === 0 ? (
            <span style={{ color: '#ef4444' }}>배정된 근무지가 없습니다. 관리자에게 문의해주세요.</span>
          ) : workplaces.length === 1 ? (
            `근무지: ${workplaces[0].name}`
          ) : (
            <label>
              근무지:{' '}
              <select
                value={selectedWorkplaceId ?? ''}
                onChange={e => setSelectedWorkplaceId(Number(e.target.value))}
                style={{ padding: '4px 8px', borderRadius: 6, border: '1px solid #d1d5db', fontSize: 13 }}
              >
                {workplaces.map(w => <option key={w.id} value={w.id}>{w.name}</option>)}
              </select>
            </label>
          )}
        </div>

        {today?.checkInAt && (
          <div style={{ display: 'flex', justifyContent: 'center', gap: 24, marginBottom: 20, fontSize: 13, color: '#374151' }}>
            <span>출근 {fmtTimeKst(today.checkInAt)}</span>
            {today.checkOutAt && <span>퇴근 {fmtTimeKst(today.checkOutAt)}</span>}
          </div>
        )}

        {canCheckIn && (
          <button
            onClick={() => checkAction.mutate('in')}
            disabled={checkAction.isPending || !selectedWorkplace}
            style={{ width: '100%', padding: '14px 0', background: '#10b981', color: '#fff', border: 'none', borderRadius: 10, fontSize: 16, fontWeight: 700, cursor: 'pointer', opacity: checkAction.isPending || !selectedWorkplace ? 0.6 : 1 }}
          >
            {checkAction.isPending ? '처리 중...' : '출근하기'}
          </button>
        )}
        {canCheckOut && (
          <button
            onClick={() => checkAction.mutate('out')}
            disabled={checkAction.isPending || !selectedWorkplace}
            style={{ width: '100%', padding: '14px 0', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 10, fontSize: 16, fontWeight: 700, cursor: 'pointer', opacity: checkAction.isPending || !selectedWorkplace ? 0.6 : 1 }}
          >
            {checkAction.isPending ? '처리 중...' : '퇴근하기'}
          </button>
        )}
        {!canCheckIn && !canCheckOut && !isLoading && (
          <p style={{ color: '#94a3b8', fontSize: 13 }}>오늘은 더 이상 출퇴근 처리를 할 수 없는 상태입니다.</p>
        )}
      </div>
    </div>
  )
}

function RegisterTab() {
  const now = new Date()
  const [mode, setMode] = useState<'month' | 'range'>('month')
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [rangeFrom, setRangeFrom] = useState(now.toISOString().slice(0, 10))
  const [rangeTo, setRangeTo] = useState(now.toISOString().slice(0, 10))
  const [queryRange, setQueryRange] = useState<{ from: string; to: string }>(() => {
    const from = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`
    const to = new Date(now.getFullYear(), now.getMonth() + 1, 0).toISOString().slice(0, 10)
    return { from, to }
  })

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ['my-attendance-register', queryRange.from, queryRange.to],
    queryFn: () => getMyAttendanceRegister(queryRange.from, queryRange.to).then(r => r.data.data),
  })

  const handleShow = () => {
    if (mode === 'month') {
      const from = `${year}-${String(month).padStart(2, '0')}-01`
      const to = new Date(year, month, 0).toISOString().slice(0, 10)
      setQueryRange({ from, to })
    } else {
      if (rangeFrom > rangeTo) { toast.error('시작일이 종료일보다 늦습니다.'); return }
      setQueryRange({ from: rangeFrom, to: rangeTo })
    }
  }

  return (
    <div>
      <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', padding: 20, marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 12, flexWrap: 'wrap' }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
            <input type="radio" checked={mode === 'month'} onChange={() => setMode('month')} /> 지정월
          </label>
          <select disabled={mode !== 'month'} value={year} onChange={e => setYear(Number(e.target.value))} style={filterInputStyle}>
            {[year - 1, year, year + 1].map(y => <option key={y} value={y}>{y}년</option>)}
          </select>
          <select disabled={mode !== 'month'} value={month} onChange={e => setMonth(Number(e.target.value))} style={filterInputStyle}>
            {Array.from({ length: 12 }, (_, i) => i + 1).map(m => <option key={m} value={m}>{m}월</option>)}
          </select>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
            <input type="radio" checked={mode === 'range'} onChange={() => setMode('range')} /> 지정 기간
          </label>
          <input type="date" disabled={mode !== 'range'} value={rangeFrom} onChange={e => setRangeFrom(e.target.value)} style={filterInputStyle} />
          <span>~</span>
          <input type="date" disabled={mode !== 'range'} value={rangeTo} onChange={e => setRangeTo(e.target.value)} style={filterInputStyle} />
          <button onClick={handleShow} style={primaryBtnStyle}>표시</button>
        </div>
      </div>

      <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', overflow: 'hidden' }}>
        {isLoading ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>로딩 중...</div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', whiteSpace: 'nowrap' }}>
              <thead>
                <tr style={{ background: '#f8fafc' }}>
                  {['날짜', '휴일구분', '근무스케줄시간', '출근시각', '퇴근시각', '근무시간', '근무스케줄 외 근무시간', '잔업시간', '심야근무시간', '휴식시간', '근태상황'].map(h => (
                    <th key={h} style={thStyle}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map((row: AttendanceRegisterRow) => (
                  <tr key={row.workDate} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={tdStyle}>{row.workDate.slice(5).replace('-', '/')}({weekdayLabel(row.workDate)})</td>
                    <td style={tdStyle}>{row.holidayLabel ?? ''}</td>
                    <td style={tdStyle}>
                      {row.scheduleStartTime ? `${fmtScheduleTime(row.scheduleStartTime)}~${fmtScheduleTime(row.scheduleEndTime)}` : ''}
                    </td>
                    <td style={tdStyle}>{fmtTimeKst(row.checkInAt)}</td>
                    <td style={tdStyle}>{fmtTimeKst(row.checkOutAt)}</td>
                    <td style={tdStyle}>{fmtMinutes(row.workMinutes)}</td>
                    <td style={tdStyle}>{fmtMinutes(row.outsideScheduleMinutes)}</td>
                    <td style={tdStyle}>{fmtMinutes(row.overtimeMinutes)}</td>
                    <td style={tdStyle}>{fmtMinutes(row.nightMinutes)}</td>
                    <td style={tdStyle}>{fmtMinutes(row.breakMinutes)}</td>
                    <td style={tdStyle}>
                      {row.status && (row.late || row.earlyLeave || row.status !== 'FINISHED') ? (
                        <span style={{ color: STATUS_COLOR[row.status], fontWeight: 600 }}>
                          {row.late ? '지각' : row.earlyLeave ? '조퇴' : STATUS_LABEL[row.status]}
                        </span>
                      ) : ''}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

export default function MyAttendancePage() {
  const [tab, setTab] = useState<'checkinout' | 'register'>('checkinout')

  return (
    <div>
      <h1 style={{ fontSize: 22, fontWeight: 700, color: '#1e293b', marginBottom: 24 }}>출근부</h1>
      <div style={{ display: 'flex', gap: 8, marginBottom: 20, borderBottom: '1px solid #e2e8f0' }}>
        {([{ key: 'checkinout', label: 'MY출퇴근' }, { key: 'register', label: 'MY출근부' }] as const).map(t => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            style={{
              padding: '10px 18px', border: 'none', background: 'transparent', cursor: 'pointer',
              fontSize: 14, fontWeight: 600,
              color: tab === t.key ? '#2563eb' : '#64748b',
              borderBottom: tab === t.key ? '2px solid #2563eb' : '2px solid transparent',
              marginBottom: -1,
            }}
          >{t.label}</button>
        ))}
      </div>
      {tab === 'checkinout' ? <CheckInOutTab /> : <RegisterTab />}
    </div>
  )
}

const filterInputStyle: React.CSSProperties = { padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 13 }
const primaryBtnStyle: React.CSSProperties = { padding: '8px 18px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontWeight: 600, fontSize: 14 }
const thStyle: React.CSSProperties = { padding: '12px 16px', textAlign: 'left', fontSize: 12, fontWeight: 600, color: '#64748b', borderBottom: '1px solid #e2e8f0' }
const tdStyle: React.CSSProperties = { padding: '12px 16px', fontSize: 14, color: '#374151' }
