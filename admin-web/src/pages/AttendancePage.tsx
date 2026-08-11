import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import {
  getDailyAttendance, getMonthlySummary, getDailyAttendanceCsv, getAttendanceRegisterBoard,
  getAttendanceScopeInfo,
  createManualAttendance, correctAttendance, closeAttendanceMonth, reopenAttendanceMonth, downloadMonthlyExcel,
} from '@/api/admin'
import { getMyAttendanceHistory } from '@/api/myAttendance'
import type { MyAttendanceHistoryRow } from '@/api/myAttendance'
import { getUsers } from '@/api/users'
import { getWorkplaces } from '@/api/workplaces'
import { getOrganizations } from '@/api/organizations'
import { useAuthStore } from '@/store/authStore'
import { orgOptionLabel, buildOrgsById, sortOrgsHierarchically } from '@/utils/organizations'
import type { AttendanceRecord, AttendanceStatus, AttendanceBoardRow, MonthlyUserSummary } from '@/types'

const STATUS_OPTIONS: AttendanceStatus[] = [
  'BEFORE_WORK', 'WORKING', 'BREAK', 'FINISHED', 'LATE', 'EARLY_LEAVE',
  'ABSENT', 'LEAVE', 'OUTSIDE_WORK', 'BUSINESS_TRIP', 'REMOTE_WORK',
]
const toIsoOrUndefined = (v: string) => v ? new Date(v).toISOString() : undefined

// ─── 공용 상수 ──────────────────────────────────────────────
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
const fmtTime = (iso?: string) => iso ? new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) : '-'
const fmtMin = (min?: number) => min != null ? `${Math.floor(min / 60)}h ${min % 60}m` : '-'
const fmtMinKorean = (min?: number | null) => min != null ? `${Math.floor(min / 60)}시간${min % 60}분` : '-'

// 이 화면(출근부 지정일)에서 출근/퇴근 시각만 입력해 보정한 경우 서버에 근무·휴식 시간이
// 별도로 계산·저장되지 않으므로(workMinutes/breakMinutes가 null로 남는다), 화면에서
// 출근시각부터 "퇴근시각(퇴근 전이면 지금)"까지의 경과 시간으로 추정해 보여준다.
// 기본 휴게시간은 12:00~13:00(1시간)로 가정하고, 그 구간이 이미 지난 뒤에 끝났을 때만
// (퇴근시각 또는 지금이 13:00 이후일 때만) 근로시간 계산에서 그 1시간을 제외한다.
const DEFAULT_BREAK_MINUTES = 60
function computeLiveWorkAndBreak(row: AttendanceBoardRow, date: string): { workMinutes: number | null; breakMinutes: number | null } {
  if (row.workMinutes != null) {
    // 실제 체크인/아웃 흐름 등으로 서버가 이미 최종 계산해 저장한 값이 있으면 그대로 쓴다.
    return { workMinutes: row.workMinutes, breakMinutes: row.breakMinutes ?? null }
  }
  if (!row.checkInAt) return { workMinutes: null, breakMinutes: null }
  const endMs = row.checkOutAt ? new Date(row.checkOutAt).getTime() : Date.now()
  const elapsedMin = Math.max(0, Math.floor((endMs - new Date(row.checkInAt).getTime()) / 60_000))
  const breakEndMs = new Date(`${date}T13:00:00+09:00`).getTime()
  const breakPassed = endMs >= breakEndMs
  const workMinutes = Math.max(0, elapsedMin - (breakPassed ? DEFAULT_BREAK_MINUTES : 0))
  return { workMinutes, breakMinutes: DEFAULT_BREAK_MINUTES }
}

// ─── 근태 수동 등록 모달 ─────────────────────────────────────
function ManualAttendanceModal({ defaultDate, onClose }: { defaultDate: string; onClose: () => void }) {
  const queryClient = useQueryClient()
  const { data: usersPage } = useQuery({ queryKey: ['users', 0], queryFn: () => getUsers(0, 200).then(r => r.data.data) })
  const { data: workplaces = [] } = useQuery({ queryKey: ['workplaces'], queryFn: () => getWorkplaces().then(r => r.data.data) })
  const users = usersPage?.content ?? []

  const [form, setForm] = useState({
    userId: '', workDate: defaultDate, workplaceId: '', checkInAt: '', checkOutAt: '',
    status: 'WORKING' as AttendanceStatus, workMinutes: '', breakMinutes: '', overtimeMinutes: '', reason: '',
  })
  const set = (k: keyof typeof form, v: string) => setForm(p => ({ ...p, [k]: v }))

  const mutation = useMutation({
    mutationFn: () => createManualAttendance({
      userId: Number(form.userId), workDate: form.workDate,
      workplaceId: form.workplaceId ? Number(form.workplaceId) : undefined,
      checkInAt: toIsoOrUndefined(form.checkInAt), checkOutAt: toIsoOrUndefined(form.checkOutAt),
      status: form.status,
      workMinutes: form.workMinutes ? Number(form.workMinutes) : undefined,
      breakMinutes: form.breakMinutes ? Number(form.breakMinutes) : undefined,
      overtimeMinutes: form.overtimeMinutes ? Number(form.overtimeMinutes) : undefined,
      reason: form.reason,
    }),
    onSuccess: () => {
      toast.success('근태가 수동 등록되었습니다.')
      queryClient.invalidateQueries({ queryKey: ['attendance-daily'] })
      onClose()
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '등록 실패'),
  })

  return (
    <Overlay>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 480, maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 24, color: '#1e293b' }}>근태 수동 등록</h2>
        <form onSubmit={e => { e.preventDefault(); mutation.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Field label="대상 직원">
            <select value={form.userId} onChange={e => set('userId', e.target.value)} required style={inputStyle}>
              <option value="">직원 선택...</option>
              {users.map(u => <option key={u.id} value={u.id}>{u.name} ({u.employeeNumber})</option>)}
            </select>
          </Field>
          <Field label="근무일"><input type="date" value={form.workDate} onChange={e => set('workDate', e.target.value)} required style={inputStyle} /></Field>
          <Field label="근무지">
            <select value={form.workplaceId} onChange={e => set('workplaceId', e.target.value)} style={inputStyle}>
              <option value="">선택 안 함</option>
              {workplaces.map(w => <option key={w.id} value={w.id}>{w.name}</option>)}
            </select>
          </Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <Field label="출근시각"><input type="datetime-local" value={form.checkInAt} onChange={e => set('checkInAt', e.target.value)} style={inputStyle} /></Field>
            <Field label="퇴근시각"><input type="datetime-local" value={form.checkOutAt} onChange={e => set('checkOutAt', e.target.value)} style={inputStyle} /></Field>
          </div>
          <Field label="근태 상태">
            <select value={form.status} onChange={e => set('status', e.target.value)} required style={inputStyle}>
              {STATUS_OPTIONS.map(s => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
            </select>
          </Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10 }}>
            <Field label="근무(분)"><input type="number" value={form.workMinutes} onChange={e => set('workMinutes', e.target.value)} style={inputStyle} /></Field>
            <Field label="휴게(분)"><input type="number" value={form.breakMinutes} onChange={e => set('breakMinutes', e.target.value)} style={inputStyle} /></Field>
            <Field label="초과(분)"><input type="number" value={form.overtimeMinutes} onChange={e => set('overtimeMinutes', e.target.value)} style={inputStyle} /></Field>
          </div>
          <Field label="등록 사유">
            <input value={form.reason} onChange={e => set('reason', e.target.value)} required style={inputStyle} placeholder="예: 시스템 오류로 인한 수동 등록" />
          </Field>
          <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
            <button type="button" onClick={onClose} style={cancelBtnStyle}>취소</button>
            <button type="submit" disabled={mutation.isPending} style={primaryBtnStyle}>{mutation.isPending ? '등록 중...' : '등록'}</button>
          </div>
        </form>
      </div>
    </Overlay>
  )
}

// ─── 일별 근태 탭 ────────────────────────────────────────────
const LOCATION_VALID_OPTIONS = [
  { value: '', label: '전체' },
  { value: 'true', label: '정상' },
  { value: 'false', label: '이상' },
]

// 레벨이 직원(파트장 미만)인 계정은 조회 대상이 본인뿐이므로, 날짜 지정 + 여러 필터 대신
// 연/월만 골라 본인 근태를 월 단위로 보여주는 간단한 화면으로 대체한다.
function SelfMonthlyView() {
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)

  const from = `${year}-${String(month).padStart(2, '0')}-01`
  const to = new Date(year, month, 0).toISOString().slice(0, 10)

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ['my-attendance-history-monthly', from, to],
    queryFn: () => getMyAttendanceHistory(from, to).then(r => r.data.data),
  })

  return (
    <>
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <select value={year} onChange={e => setYear(Number(e.target.value))} style={filterInputStyle}>
          {[year - 1, year, year + 1].map(y => <option key={y} value={y}>{y}년</option>)}
        </select>
        <select value={month} onChange={e => setMonth(Number(e.target.value))} style={filterInputStyle}>
          {Array.from({ length: 12 }, (_, i) => i + 1).map(m => <option key={m} value={m}>{m}월</option>)}
        </select>
      </div>
      <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', overflow: 'auto' }}>
        {isLoading ? <Loading /> : rows.length === 0 ? <Empty text="해당 월의 근태 기록이 없습니다." /> : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#f8fafc' }}>
                {['날짜', '상태', '근무지', '출근', '퇴근', '근무', '휴식'].map(h => (
                  <th key={h} style={thStyle}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((r: MyAttendanceHistoryRow) => (
                <tr key={r.attendanceId} style={{ borderBottom: '1px solid #f1f5f9' }}>
                  <td style={tdStyle}>{r.workDate}</td>
                  <td style={tdStyle}>
                    <Badge color={STATUS_COLOR[r.status]}>{r.late ? '지각' : r.earlyLeave ? '조퇴' : STATUS_LABEL[r.status]}</Badge>
                  </td>
                  <td style={tdStyle}>{r.workplaceName ?? '-'}</td>
                  <td style={tdStyle}>{fmtTime(r.checkInAt)}</td>
                  <td style={tdStyle}>{fmtTime(r.checkOutAt)}</td>
                  <td style={tdStyle}>{fmtMin(r.workMinutes)}</td>
                  <td style={tdStyle}>{fmtMin(r.breakMinutes)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}

function DailyTab() {
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [showManual, setShowManual] = useState(false)
  const [workplaceId, setWorkplaceId] = useState('')
  const [organizationId, setOrganizationId] = useState('')
  const [employeeName, setEmployeeName] = useState('')
  const [status, setStatus] = useState<AttendanceStatus | ''>('')
  const [lateOnly, setLateOnly] = useState(false)
  const [locationValid, setLocationValid] = useState('')

  const { data: scopeInfo, isLoading: isScopeLoading } = useQuery({
    queryKey: ['attendance-scope-info'],
    queryFn: () => getAttendanceScopeInfo().then(r => r.data.data),
  })

  const { data: workplaces = [] } = useQuery({ queryKey: ['workplaces'], queryFn: () => getWorkplaces().then(r => r.data.data) })
  const { data: organizations = [] } = useQuery({ queryKey: ['organizations'], queryFn: () => getOrganizations().then(r => r.data.data) })
  // 상위부서명 표시는 전체 조직 목록 기준으로 계산해야 한다 — 선택 가능한 목록(visibleOrganizations)만으로
  // 만들면, 본인 조직의 상위부서가 그 목록 밖에 있을 때(조회범위 밖) 이름을 못 찾는다.
  const orgsById = buildOrgsById(organizations)

  // scopeInfo.workplaceIds/organizationIds가 null이면 전체 조회 가능(SYSADMIN 등), 배열이면 그 범위로 좁혀서 보여준다.
  const visibleWorkplaces = scopeInfo?.workplaceIds
    ? workplaces.filter(w => scopeInfo.workplaceIds!.includes(w.id))
    : workplaces
  const visibleOrganizations = sortOrgsHierarchically(scopeInfo?.organizationIds
    ? organizations.filter(o => scopeInfo.organizationIds!.includes(o.id))
    : organizations)

  const filters = {
    workplaceId: workplaceId ? Number(workplaceId) : undefined,
    organizationId: organizationId ? Number(organizationId) : undefined,
    employeeName: employeeName || undefined,
    status: status || undefined,
    lateOnly: lateOnly || undefined,
    locationValid: locationValid ? locationValid === 'true' : undefined,
  }

  const { data, isLoading } = useQuery({
    queryKey: ['attendance-daily', date, filters],
    queryFn: () => getDailyAttendance(date, 0, 100, filters).then(r => r.data.data),
    enabled: !scopeInfo?.employeeLevel,
  })
  const records = data?.content ?? []

  if (isScopeLoading) return <Loading />
  if (scopeInfo?.employeeLevel) return <SelfMonthlyView />

  // 파트장 이상이어도 하위 직원이 없으면(대상이 본인뿐) 근무지/부서/직원명 검색은 의미가 없어 숨긴다.
  const showPersonFilters = !!scopeInfo?.hasSubordinates

  return (
    <>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 12, alignItems: 'center' }}>
        <input type="date" value={date} onChange={e => setDate(e.target.value)} style={filterInputStyle} />
        {showPersonFilters && (
          <select value={workplaceId} onChange={e => setWorkplaceId(e.target.value)} style={filterInputStyle}>
            <option value="">근무지 전체</option>
            {visibleWorkplaces.map(w => <option key={w.id} value={w.id}>{w.name}</option>)}
          </select>
        )}
        {showPersonFilters && (
          <select value={organizationId} onChange={e => setOrganizationId(e.target.value)} style={filterInputStyle}>
            <option value="">부서 전체</option>
            {visibleOrganizations.map(o => <option key={o.id} value={o.id}>{orgOptionLabel(o, orgsById)}</option>)}
          </select>
        )}
        {showPersonFilters && (
          <input placeholder="직원명 검색" value={employeeName} onChange={e => setEmployeeName(e.target.value)} style={filterInputStyle} />
        )}
        <select value={status} onChange={e => setStatus(e.target.value as AttendanceStatus | '')} style={filterInputStyle}>
          <option value="">상태 전체</option>
          {STATUS_OPTIONS.map(s => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
        </select>
        <select value={locationValid} onChange={e => setLocationValid(e.target.value)} style={filterInputStyle}>
          {LOCATION_VALID_OPTIONS.map(o => <option key={o.value} value={o.value}>위치 {o.label}</option>)}
        </select>
        <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: '#374151' }}>
          <input type="checkbox" checked={lateOnly} onChange={e => setLateOnly(e.target.checked)} /> 지각만
        </label>
      </div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginBottom: 16 }}>
        <button onClick={() => setShowManual(true)} style={primaryBtnStyle}>+ 수동 등록</button>
        <button
          onClick={() => getDailyAttendanceCsv(date)}
          style={{ padding: '8px 14px', background: '#10b981', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 600 }}
        >CSV 내보내기</button>
      </div>
      <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', overflow: 'auto', maxHeight: '60vh' }}>
        {isLoading ? <Loading /> : records.length === 0 ? <Empty text="해당 날짜의 근태 기록이 없습니다." /> : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#f8fafc', position: 'sticky', top: 0, zIndex: 1 }}>
                {['사번', '이름', '상태', '근무지', '출근', '퇴근', '처리방식', '근무시간', '휴식시간', '근무스케줄 외 근무시간'].map(h => (
                  <th key={h} style={thStyle}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {records.map((r: AttendanceRecord) => (
                <tr key={r.attendanceId} style={{ borderBottom: '1px solid #f1f5f9' }}>
                  <td style={tdStyle}>{r.employeeNumber}</td>
                  <td style={{ ...tdStyle, fontWeight: 500 }}>{r.userName}</td>
                  <td style={tdStyle}>
                    <Badge color={STATUS_COLOR[r.status]}>{STATUS_LABEL[r.status]}</Badge>
                    {r.closed && <span style={{ marginLeft: 6, fontSize: 11, color: '#94a3b8' }}>🔒마감</span>}
                  </td>
                  <td style={tdStyle}>{r.workplaceName ?? '-'}</td>
                  <td style={tdStyle}>{fmtTime(r.checkInAt)}</td>
                  <td style={tdStyle}>{fmtTime(r.checkOutAt)}</td>
                  <td style={tdStyle}>{r.processMethod === 'GPS' ? 'GPS' : r.processMethod === 'MANUAL' ? '관리자 수동' : '-'}</td>
                  <td style={tdStyle}>{fmtMin(r.workMinutes)}</td>
                  <td style={tdStyle}>{fmtMin(r.breakMinutes)}</td>
                  <td style={tdStyle}>{r.overtimeMinutes ? `+${r.overtimeMinutes}m` : '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {data && <p style={{ marginTop: 10, fontSize: 12, color: '#94a3b8' }}>총 {data.totalElements}건</p>}
      {showManual && <ManualAttendanceModal defaultDate={date} onClose={() => setShowManual(false)} />}
    </>
  )
}

// ─── 출근부(지정일) 탭 ────────────────────────────────────────
// 파트장 이상 권한이 하루 단위로 하위 직원의 출근/퇴근 시각을 한 화면에서 일괄 보정한다.
// 시각은 브라우저 로컬시간(Asia/Seoul) 기준으로 입력받아 ISO로 변환한다 — 근태 보정 모달과 동일한 방식.
const toLocalHHMM = (iso?: string) => {
  if (!iso) return ''
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
const toIsoFromDateAndTime = (date: string, hhmm: string) =>
  hhmm ? new Date(`${date}T${hhmm}:00`).toISOString() : undefined
const HHMM_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/
const isValidHHMM = (v: string) => v === '' || HHMM_PATTERN.test(v)
// 숫자만 입력해도 "0900" → "09:00"처럼 두 자리(시)를 넘어가는 순간 ":"을 자동으로 끼워 넣는다.
const autoFormatHHMM = (raw: string) => {
  const digits = raw.replace(/\D/g, '').slice(0, 4)
  return digits.length <= 2 ? digits : `${digits.slice(0, 2)}:${digits.slice(2)}`
}

interface BoardEdit { checkInAt: string; checkOutAt: string }

// 이 컴포넌트를 RegisterBoardTab 내부에 중첩 정의하면 렌더마다 새 함수 정체성이 생겨
// <SaveBar/> 하위의 사유 입력 <input>이 매 렌더마다(=매 키 입력마다) 통째로 리마운트되고,
// 그 결과 한글 조합 입력(IME composition) 중간에 DOM 노드가 사라져 조합이 깨진다
// (증상: 한글이 아니라 엉뚱한 한자/깨진 글자만 입력되는 것처럼 보임).
// 컴포넌트를 모듈 스코프로 끌어올려 함수 정체성을 고정해야 이 문제가 사라진다.
function AttendanceReasonBar({
  reason, onReasonChange, onSave, saving, changedCount,
  bulkField, onBulkFieldChange, bulkTime, onBulkTimeChange, onApplyBulk, selectedCount,
}: {
  reason: string
  onReasonChange: (v: string) => void
  onSave: () => void
  saving: boolean
  changedCount: number
  bulkField: 'checkInAt' | 'checkOutAt'
  onBulkFieldChange: (v: 'checkInAt' | 'checkOutAt') => void
  bulkTime: string
  onBulkTimeChange: (v: string) => void
  onApplyBulk: () => void
  selectedCount: number
}) {
  return (
    <div style={{ display: 'flex', gap: 8, alignItems: 'center', justifyContent: 'flex-end', marginBottom: 12, flexWrap: 'wrap' }}>
      <select value={bulkField} onChange={e => onBulkFieldChange(e.target.value as 'checkInAt' | 'checkOutAt')} style={filterInputStyle}>
        <option value="checkInAt">출근시각</option>
        <option value="checkOutAt">퇴근시각</option>
      </select>
      <input
        placeholder="09:00" inputMode="numeric" maxLength={5}
        value={bulkTime}
        onChange={e => onBulkTimeChange(autoFormatHHMM(e.target.value))}
        style={{ ...filterInputStyle, width: 70, textAlign: 'center' }}
      />
      <button onClick={onApplyBulk} style={primaryBtnStyle}>
        선택 직원에 적용{selectedCount > 0 ? ` (${selectedCount})` : ''}
      </button>
      <input
        placeholder="보정 사유 (변경 시 필수)"
        value={reason}
        onChange={e => onReasonChange(e.target.value)}
        style={{ ...filterInputStyle, width: 240 }}
      />
      <button onClick={onSave} disabled={saving} style={primaryBtnStyle}>
        {saving ? '저장 중...' : `변경 저장${changedCount > 0 ? ` (${changedCount})` : ''}`}
      </button>
    </div>
  )
}

function RegisterBoardTab() {
  const queryClient = useQueryClient()
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [organizationId, setOrganizationId] = useState('')
  const [employeeName, setEmployeeName] = useState('')
  const [edits, setEdits] = useState<Record<number, BoardEdit>>({})
  const [reason, setReason] = useState('')
  const [selectedUserIds, setSelectedUserIds] = useState<Set<number>>(new Set())
  const [bulkField, setBulkField] = useState<'checkInAt' | 'checkOutAt'>('checkInAt')
  const [bulkTime, setBulkTime] = useState('')
  // 아직 퇴근하지 않은 직원의 근로시간은 "지금"을 기준으로 추정해 보여주므로, 분 단위로 다시 계산되도록 주기적으로 리렌더링한다.
  const [, retick] = useState(0)
  useEffect(() => {
    const timer = setInterval(() => retick(t => t + 1), 60_000)
    return () => clearInterval(timer)
  }, [])

  const { data: scopeInfo } = useQuery({
    queryKey: ['attendance-scope-info'],
    queryFn: () => getAttendanceScopeInfo().then(r => r.data.data),
  })
  const { data: organizations = [] } = useQuery({ queryKey: ['organizations'], queryFn: () => getOrganizations().then(r => r.data.data) })
  const orgsById = buildOrgsById(organizations)

  // scopeInfo.organizationIds가 null이면 전체 조회 가능(SYSADMIN 등), 배열이면 그 범위(본인 소속부서+하위부서)로 좁혀서 보여준다.
  const visibleOrganizations = sortOrgsHierarchically(scopeInfo?.organizationIds
    ? organizations.filter(o => scopeInfo.organizationIds!.includes(o.id))
    : organizations)

  const filters = {
    organizationId: organizationId ? Number(organizationId) : undefined,
    employeeName: employeeName || undefined,
  }
  const { data: rows = [], isLoading } = useQuery({
    queryKey: ['attendance-register-board', date, filters],
    queryFn: () => getAttendanceRegisterBoard(date, filters).then(r => r.data.data),
  })

  // 날짜·필터가 바뀌어 새 데이터를 불러오면 이전 화면의 미저장 편집 상태는 초기화한다.
  // 근태 기록이 아직 없는 직원도 편집할 수 있어야 하므로 attendanceId가 아니라 userId로 편집 상태를 관리한다.
  useEffect(() => { setEdits({}); setSelectedUserIds(new Set()); setBulkTime('') }, [date, organizationId, employeeName])

  const getEdit = (row: AttendanceBoardRow): BoardEdit =>
    edits[row.userId] ?? { checkInAt: toLocalHHMM(row.checkInAt), checkOutAt: toLocalHHMM(row.checkOutAt) }

  const setEdit = (row: AttendanceBoardRow, field: keyof BoardEdit, value: string) => {
    setEdits(prev => ({ ...prev, [row.userId]: { ...getEdit(row), [field]: value } }))
  }

  const allSelected = rows.length > 0 && rows.every(r => selectedUserIds.has(r.userId))
  const toggleSelectAll = (checked: boolean) => {
    setSelectedUserIds(checked ? new Set(rows.map(r => r.userId)) : new Set())
  }
  const toggleSelectRow = (userId: number, checked: boolean) => {
    setSelectedUserIds(prev => {
      const next = new Set(prev)
      if (checked) next.add(userId); else next.delete(userId)
      return next
    })
  }

  // 체크된 직원들에게 선택한 필드(출근/퇴근시각)의 값을 한 번에 채워 넣는다. 실제 저장은 기존
  // "변경 저장" 버튼이 그대로 담당하므로, 여기서는 로컬 편집 상태(edits)만 채운다.
  const handleApplyBulkField = () => {
    if (selectedUserIds.size === 0) { toast.error('적용할 직원을 선택해주세요.'); return }
    if (!bulkTime) { toast.error('적용할 시각을 입력해주세요.'); return }
    if (!isValidHHMM(bulkTime)) { toast.error('시각 형식이 올바르지 않습니다. (예: 09:00)'); return }

    setEdits(prev => {
      const next = { ...prev }
      for (const row of rows) {
        if (row.closed || !selectedUserIds.has(row.userId)) continue
        next[row.userId] = { ...(next[row.userId] ?? getEdit(row)), [bulkField]: bulkTime }
      }
      return next
    })
    toast.success(`${selectedUserIds.size}명에게 적용되었습니다. 내용을 확인한 뒤 변경 저장을 눌러주세요.`)
  }

  const changedRows = rows.filter(row => {
    if (row.closed) return false
    const edit = edits[row.userId]
    if (!edit) return false
    return edit.checkInAt !== toLocalHHMM(row.checkInAt) || edit.checkOutAt !== toLocalHHMM(row.checkOutAt)
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      for (const row of changedRows) {
        const edit = edits[row.userId]
        const finalReason = reason || '출근부(지정일) 화면에서 일괄 보정'
        if (row.attendanceId) {
          // row.workplaceId는 이미 기록된 근무지가 있으면 그 값, 없으면 이 직원에게 배정된 근무지다.
          // 항상 같이 보내면 기존 값은 그대로 유지되고, 비어 있던 경우에만 배정된 근무지로 채워진다.
          const payload: Record<string, unknown> = { reason: finalReason, workplaceId: row.workplaceId }
          if (edit.checkInAt !== toLocalHHMM(row.checkInAt)) payload.checkInAt = toIsoFromDateAndTime(date, edit.checkInAt)
          if (edit.checkOutAt !== toLocalHHMM(row.checkOutAt)) payload.checkOutAt = toIsoFromDateAndTime(date, edit.checkOutAt)
          await correctAttendance(row.attendanceId, payload as any)
        } else {
          // 그날 근태 기록이 아예 없는 직원 — 출근/퇴근 시각을 채워 새 기록을 만든다.
          await createManualAttendance({
            userId: row.userId,
            workDate: date,
            workplaceId: row.workplaceId,
            checkInAt: toIsoFromDateAndTime(date, edit.checkInAt),
            checkOutAt: toIsoFromDateAndTime(date, edit.checkOutAt),
            status: edit.checkOutAt ? 'FINISHED' : 'WORKING',
            reason: finalReason,
          })
        }
      }
    },
    onSuccess: () => {
      toast.success(`${changedRows.length}건 저장되었습니다.`)
      setEdits({})
      setReason('')
      setSelectedUserIds(new Set())
      setBulkTime('')
      queryClient.invalidateQueries({ queryKey: ['attendance-register-board'] })
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '저장 실패'),
  })

  const handleSave = () => {
    if (changedRows.length === 0) { toast.error('변경된 내용이 없습니다.'); return }
    if (!reason.trim()) { toast.error('보정 사유를 입력해주세요.'); return }
    const invalidRow = changedRows.find(row => {
      const edit = edits[row.userId]
      return !isValidHHMM(edit.checkInAt) || !isValidHHMM(edit.checkOutAt)
    })
    if (invalidRow) { toast.error(`${invalidRow.userName}의 시각 형식이 올바르지 않습니다. (예: 09:00)`); return }
    saveMutation.mutate()
  }

  const saveBar = (
    <AttendanceReasonBar
      reason={reason} onReasonChange={setReason} onSave={handleSave}
      saving={saveMutation.isPending} changedCount={changedRows.length}
      bulkField={bulkField} onBulkFieldChange={setBulkField}
      bulkTime={bulkTime} onBulkTimeChange={setBulkTime}
      onApplyBulk={handleApplyBulkField} selectedCount={selectedUserIds.size}
    />
  )

  return (
    <>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 12, alignItems: 'center' }}>
        <input type="date" value={date} onChange={e => setDate(e.target.value)} style={filterInputStyle} />
        <select value={organizationId} onChange={e => setOrganizationId(e.target.value)} style={filterInputStyle}>
          <option value="">소속그룹 전체</option>
          {visibleOrganizations.map(o => <option key={o.id} value={o.id}>{orgOptionLabel(o, orgsById)}</option>)}
        </select>
        <input placeholder="직원명 검색" value={employeeName} onChange={e => setEmployeeName(e.target.value)} style={filterInputStyle} />
      </div>

      {saveBar}

      <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', overflow: 'auto', maxHeight: '60vh' }}>
        {isLoading ? <Loading /> : rows.length === 0 ? <Empty text="조회된 직원이 없습니다." /> : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#f8fafc', position: 'sticky', top: 0, zIndex: 1 }}>
                <th style={{ ...thStyle, whiteSpace: 'nowrap' }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: 4, fontWeight: 600, cursor: 'pointer' }}>
                    <input type="checkbox" checked={allSelected} onChange={e => toggleSelectAll(e.target.checked)} />
                    전체선택
                  </label>
                </th>
                {['직원', '근태상황', '수정요청', '근무스케줄', '출근시각', '퇴근시각', '근무시간', '휴식시간', '잔업시간'].map(h => (
                  <th key={h} style={thStyle}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row: AttendanceBoardRow) => {
                const edit = getEdit(row)
                const editable = !row.closed
                const live = computeLiveWorkAndBreak(row, date)
                return (
                  <tr key={row.userId} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={tdStyle}>
                      <input type="checkbox" checked={selectedUserIds.has(row.userId)}
                        onChange={e => toggleSelectRow(row.userId, e.target.checked)} />
                    </td>
                    <td style={{ ...tdStyle, fontWeight: 500 }}>{row.userName} <span style={{ color: '#94a3b8', fontSize: 12 }}>({row.employeeNumber})</span></td>
                    <td style={tdStyle}>
                      {row.leaveTypeLabel ? <Badge color={STATUS_COLOR.LEAVE}>{row.leaveTypeLabel}</Badge>
                        : row.status ? <Badge color={STATUS_COLOR[row.status]}>{STATUS_LABEL[row.status]}</Badge> : '-'}
                      {row.closed && <span style={{ marginLeft: 6, fontSize: 11, color: '#94a3b8' }}>🔒마감</span>}
                    </td>
                    <td style={tdStyle}>{row.hasPendingChangeRequest ? <Badge color="#f97316">신청중</Badge> : ''}</td>
                    <td style={tdStyle}>
                      {row.scheduleStartTime ? `${row.scheduleStartTime.slice(0, 5)} ~ ${row.scheduleEndTime?.slice(0, 5)}` : '-'}
                    </td>
                    <td style={tdStyle}>
                      <input type="text" inputMode="numeric" placeholder="09:00" maxLength={5}
                        value={edit.checkInAt} disabled={!editable}
                        onChange={e => setEdit(row, 'checkInAt', autoFormatHHMM(e.target.value))}
                        style={{
                          ...filterInputStyle, width: 70, textAlign: 'center', opacity: editable ? 1 : 0.5,
                          borderColor: !isValidHHMM(edit.checkInAt) ? '#ef4444' : edit.checkInAt ? '#1e293b' : undefined,
                          borderWidth: edit.checkInAt ? 2 : 1,
                        }} />
                    </td>
                    <td style={tdStyle}>
                      <input type="text" inputMode="numeric" placeholder="18:00" maxLength={5}
                        value={edit.checkOutAt} disabled={!editable}
                        onChange={e => setEdit(row, 'checkOutAt', autoFormatHHMM(e.target.value))}
                        style={{
                          ...filterInputStyle, width: 70, textAlign: 'center', opacity: editable ? 1 : 0.5,
                          borderColor: !isValidHHMM(edit.checkOutAt) ? '#ef4444' : edit.checkOutAt ? '#1e293b' : undefined,
                          borderWidth: edit.checkOutAt ? 2 : 1,
                        }} />
                    </td>
                    <td style={tdStyle}>{fmtMinKorean(live.workMinutes)}</td>
                    <td style={tdStyle}>{fmtMinKorean(live.breakMinutes)}</td>
                    <td style={tdStyle}>{fmtMinKorean(row.overtimeMinutes)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>

      <div style={{ marginTop: 16 }}>{saveBar}</div>
    </>
  )
}

// ─── 월별 통계 탭 ────────────────────────────────────────────
function MonthlyTab() {
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)

  const { data: summaries = [], isLoading } = useQuery({
    queryKey: ['attendance-monthly', year, month],
    queryFn: () => getMonthlySummary(year, month).then(r => r.data.data),
  })

  const closeMutation = useMutation({
    mutationFn: () => closeAttendanceMonth(year, month),
    onSuccess: (res) => toast.success(`${res.data.data}건의 근태가 마감되었습니다.`),
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '마감 실패'),
  })
  const reopenMutation = useMutation({
    mutationFn: () => reopenAttendanceMonth(year, month),
    onSuccess: (res) => toast.success(`${res.data.data}건의 근태가 재오픈되었습니다.`),
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '재오픈 실패'),
  })

  // 팀 합계
  const totalPresent = summaries.reduce((s, r) => s + r.presentDays, 0)
  const totalLate    = summaries.reduce((s, r) => s + r.lateDays, 0)
  const totalAbsent  = summaries.reduce((s, r) => s + r.absentDays, 0)

  return (
    <>
      {/* 월 선택 및 도구 */}
      <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginBottom: 16, flexWrap: 'wrap' }}>
        <select value={year} onChange={e => setYear(Number(e.target.value))}
          style={{ padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14 }}>
          {[2024, 2025, 2026].map(y => <option key={y} value={y}>{y}년</option>)}
        </select>
        <select value={month} onChange={e => setMonth(Number(e.target.value))}
          style={{ padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14 }}>
          {Array.from({ length: 12 }, (_, i) => i + 1).map(m => (
            <option key={m} value={m}>{m}월</option>
          ))}
        </select>
        <button onClick={() => downloadMonthlyExcel(year, month)} style={{ padding: '8px 14px', background: '#10b981', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 600 }}>
          엑셀 다운로드
        </button>
        <button
          onClick={() => { if (confirm(`${year}년 ${month}월 근태를 마감하시겠습니까? 마감 후에는 보정·수정요청 승인이 불가합니다.`)) closeMutation.mutate() }}
          disabled={closeMutation.isPending}
          style={{ padding: '8px 14px', background: '#1e293b', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 600 }}
        >마감</button>
        <button
          onClick={() => { if (confirm(`${year}년 ${month}월 마감을 재오픈하시겠습니까?`)) reopenMutation.mutate() }}
          disabled={reopenMutation.isPending}
          style={{ padding: '8px 14px', background: '#fff', color: '#374151', border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 600 }}
        >재오픈</button>
      </div>

      {isLoading ? <Loading /> : summaries.length === 0 ? <Empty text="데이터가 없습니다." /> : (
        <>
          {/* 팀 요약 차트 */}
          <div style={{ background: '#fff', borderRadius: 12, padding: 24, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', marginBottom: 20 }}>
            <h2 style={{ fontSize: 15, fontWeight: 600, color: '#1e293b', marginBottom: 20 }}>팀 월간 근태 현황</h2>
            <MiniBarChart
              items={summaries.map(s => ({ label: s.userName, present: s.presentDays, late: s.lateDays, absent: s.absentDays }))}
            />
            <div style={{ display: 'flex', gap: 20, marginTop: 16, justifyContent: 'center' }}>
              {[['출근', '#10b981', totalPresent], ['지각', '#f97316', totalLate], ['결근', '#ef4444', totalAbsent]].map(([label, color, val]) => (
                <div key={label as string} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: '#64748b' }}>
                  <div style={{ width: 10, height: 10, background: color as string, borderRadius: 2 }} />
                  {label}: <b style={{ color: '#1e293b' }}>{val}</b>일
                </div>
              ))}
            </div>
          </div>

          {/* 개인별 상세 테이블 */}
          <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', overflow: 'auto', maxHeight: '60vh' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ background: '#f8fafc', position: 'sticky', top: 0, zIndex: 1 }}>
                  {['사번', '이름', '기록일수', '출근', '지각', '조퇴', '결근', '총근무', '초과'].map(h => (
                    <th key={h} style={thStyle}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {summaries.map((s: MonthlyUserSummary) => (
                  <tr key={s.userId} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={tdStyle}>{s.employeeNumber}</td>
                    <td style={{ ...tdStyle, fontWeight: 500 }}>{s.userName}</td>
                    <td style={tdStyle}>{s.workingDays}</td>
                    <td style={tdStyle}><NumChip val={s.presentDays} color="#10b981" /></td>
                    <td style={tdStyle}><NumChip val={s.lateDays} color="#f97316" /></td>
                    <td style={tdStyle}><NumChip val={s.earlyLeaveDays} color="#a855f7" /></td>
                    <td style={tdStyle}><NumChip val={s.absentDays} color="#ef4444" /></td>
                    <td style={tdStyle}>{fmtMin(s.totalWorkMinutes)}</td>
                    <td style={tdStyle}>{s.totalOvertimeMinutes > 0 ? `+${fmtMin(s.totalOvertimeMinutes)}` : '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </>
  )
}

// ─── SVG 막대 차트 ───────────────────────────────────────────
interface BarItem { label: string; present: number; late: number; absent: number }

function MiniBarChart({ items }: { items: BarItem[] }) {
  const max = Math.max(...items.map(i => i.present + i.late + i.absent), 1)
  const BAR_H = 120

  return (
    <div style={{ overflowX: 'auto' }}>
      <svg width={Math.max(items.length * 60, 300)} height={BAR_H + 40} style={{ display: 'block' }}>
        {items.map((item, i) => {
          const x = i * 60 + 10
          const total = item.present + item.late + item.absent
          const scale = (v: number) => (v / max) * BAR_H
          const presentH = scale(item.present)
          const lateH    = scale(item.late)
          const absentH  = scale(item.absent)
          const totalH   = scale(total)

          return (
            <g key={i}>
              {/* 결근 (bottom) */}
              <rect x={x} y={BAR_H - absentH} width={40} height={absentH} fill="#ef4444" rx={2} />
              {/* 지각 */}
              <rect x={x} y={BAR_H - absentH - lateH} width={40} height={lateH} fill="#f97316" rx={2} />
              {/* 출근 */}
              <rect x={x} y={BAR_H - totalH} width={40} height={presentH} fill="#10b981" rx={2} />
              {/* 이름 */}
              <text x={x + 20} y={BAR_H + 18} textAnchor="middle" fontSize={10} fill="#64748b">
                {item.label.length > 4 ? item.label.slice(0, 4) : item.label}
              </text>
              {/* 수치 */}
              {total > 0 && (
                <text x={x + 20} y={BAR_H - totalH - 4} textAnchor="middle" fontSize={9} fill="#374151">
                  {total}
                </text>
              )}
            </g>
          )
        })}
      </svg>
    </div>
  )
}

// ─── 메인 페이지 ────────────────────────────────────────────
const TAB_LABEL = { daily: '일별', monthly: '월별', board: '출근부(일괄수정)' } as const

export default function AttendancePage() {
  const { role, level } = useAuthStore()
  // 파트장 이상 권한(레벨)만 출근부(지정일) 탭에서 하위 직원의 출근/퇴근을 보정할 수 있다.
  // 실제 접근 제어는 백엔드가 최종 판단하며, 여기서는 탭 노출 여부만 결정한다.
  const canManageRegisterBoard = role === 'MANAGER' || role === 'HR_ADMIN' || role === 'SYSTEM_ADMIN'
    || (!!level && level !== 'EMPLOYEE')

  const tabs = (['daily', 'monthly', ...(canManageRegisterBoard ? ['board'] as const : [])] as const)
  const [tab, setTab] = useState<'daily' | 'monthly' | 'board'>('daily')

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, color: '#1e293b' }}>근태 조회</h1>
        <div style={{ display: 'flex', background: '#f1f5f9', borderRadius: 8, padding: 4 }}>
          {tabs.map(t => (
            <button key={t} onClick={() => setTab(t)} style={{
              padding: '6px 18px', border: 'none', borderRadius: 6, cursor: 'pointer', fontSize: 14, fontWeight: 500,
              background: tab === t ? '#fff' : 'transparent',
              color: tab === t ? '#1e293b' : '#64748b',
              boxShadow: tab === t ? '0 1px 4px rgba(0,0,0,0.08)' : 'none',
            }}>
              {TAB_LABEL[t]}
            </button>
          ))}
        </div>
      </div>
      {tab === 'daily' ? <DailyTab /> : tab === 'monthly' ? <MonthlyTab /> : <RegisterBoardTab />}
    </div>
  )
}

// ─── 공용 컴포넌트 ───────────────────────────────────────────
function Badge({ color, children }: { color: string; children: React.ReactNode }) {
  return (
    <span style={{ padding: '3px 10px', borderRadius: 20, fontSize: 12, background: color + '20', color, fontWeight: 600 }}>
      {children}
    </span>
  )
}
function NumChip({ val, color }: { val: number; color: string }) {
  return val > 0
    ? <span style={{ fontWeight: 600, color }}>{val}</span>
    : <span style={{ color: '#d1d5db' }}>0</span>
}
function Loading() { return <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>로딩 중...</div> }
function Empty({ text }: { text: string }) { return <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>{text}</div> }
function Overlay({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100 }}>
      {children}
    </div>
  )
}
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: 13, fontWeight: 500, color: '#374151', marginBottom: 4 }}>{label}</label>
      {children}
    </div>
  )
}

const thStyle: React.CSSProperties = { padding: '12px 16px', textAlign: 'left', fontSize: 12, fontWeight: 600, color: '#64748b', borderBottom: '1px solid #e2e8f0' }
const tdStyle: React.CSSProperties = { padding: '12px 16px', fontSize: 14, color: '#374151' }
const inputStyle: React.CSSProperties = { width: '100%', padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14 }
const filterInputStyle: React.CSSProperties = { padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 13 }
const cancelBtnStyle: React.CSSProperties = { flex: 1, padding: 10, border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontSize: 14, background: '#fff', color: '#374151' }
const primaryBtnStyle: React.CSSProperties = { padding: '8px 18px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontWeight: 600, fontSize: 14 }
