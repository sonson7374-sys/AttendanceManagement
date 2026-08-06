import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getHolidays, getHolidayPresets, createHoliday, updateHoliday, deleteHoliday, bulkCreateHolidays,
} from '@/api/holidays'
import { getApprovedLeaveCalendar, bulkUploadLeaveRequests, downloadLeaveBulkImportTemplate } from '@/api/leaveRequests'
import type { BulkLeaveImportResponse } from '@/api/leaveRequests'
import type { Holiday, HolidayPreset, HolidayType, LeaveRequest, LeaveRequestType } from '@/types'
import { usePermissions } from '@/hooks/usePermissions'
import { useAuthStore } from '@/store/authStore'
import toast from 'react-hot-toast'

const HOLIDAY_TYPE_LABEL: Record<HolidayType, string> = {
  PUBLIC: '공휴일', SUBSTITUTE: '대체공휴일', COMPANY: '회사휴일', WEEKEND: '주말',
}
const HOLIDAY_TYPE_COLOR: Record<HolidayType, string> = {
  PUBLIC: '#ef4444', SUBSTITUTE: '#f97316', COMPANY: '#8b5cf6', WEEKEND: '#64748b',
}
// 개별 휴일 등록/수정 시 고를 수 있는 유형. "주말"은 월별 일괄 등록 버튼으로만 만든다.
const MANUAL_HOLIDAY_TYPES: HolidayType[] = ['PUBLIC', 'SUBSTITUTE', 'COMPANY']
const LEAVE_TYPE_LABEL: Record<LeaveRequestType, string> = {
  ANNUAL: '연차', HALF_DAY: '반차', HOURLY: '반반차', SICK: '병가',
  OFFICIAL: '공가', OVERTIME: '연장근무', HOLIDAY_WORK: '휴일근무',
  ZERO_DAY: '대체휴가', EARLY: '조기퇴근',
}

// 서버가 UTC Instant로 내려주는 값을 관리자 브라우저 로캘과 무관하게 Asia/Seoul 기준
// yyyy-MM-dd 문자열로 바꾼다 (달력 날짜 매칭용).
const toKstDate = (iso: string) => new Date(iso).toLocaleDateString('sv-SE', { timeZone: 'Asia/Seoul' })
const toKstTime = (iso: string) => new Date(iso).toLocaleTimeString('ko-KR', { timeZone: 'Asia/Seoul', hour: '2-digit', minute: '2-digit', hour12: false })
const pad2 = (n: number) => String(n).padStart(2, '0')
const weekdayOf = (year: number, month: number, day: number) => new Date(year, month - 1, day).getDay() // 일=0..토=6
const DAY_CELL_MAX_LEAVES = 3

// 휴가 하나의 시작~종료를 관리자가 알아보기 쉬운 문자열로 만든다.
// 같은 날 안에서 끝나면 시간만, 여러 날에 걸치면 날짜까지 함께 보여준다.
const formatLeaveRange = (l: LeaveRequest) => {
  const startDate = toKstDate(l.startAt)
  const endDate = toKstDate(l.endAt)
  if (startDate === endDate) return `${startDate} ${toKstTime(l.startAt)}~${toKstTime(l.endAt)}`
  return `${startDate} ${toKstTime(l.startAt)} ~ ${endDate} ${toKstTime(l.endAt)}`
}

interface FormData { holidayDate: string; name: string; holidayType: HolidayType }
const DEFAULT_FORM: FormData = { holidayDate: '', name: '', holidayType: 'PUBLIC' }

function HolidayModal({ holiday, onClose }: { holiday?: Holiday; onClose: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState<FormData>(holiday ? {
    holidayDate: holiday.holidayDate, name: holiday.name, holidayType: holiday.holidayType,
  } : DEFAULT_FORM)
  const set = <K extends keyof FormData>(k: K, v: FormData[K]) => setForm(p => ({ ...p, [k]: v }))

  const mutation = useMutation({
    mutationFn: () => holiday ? updateHoliday(holiday.id, form) : createHoliday(form),
    onSuccess: () => {
      toast.success(holiday ? '수정되었습니다.' : '등록되었습니다.')
      qc.invalidateQueries({ queryKey: ['holidays'] })
      onClose()
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '오류가 발생했습니다.'),
  })

  return (
    <Overlay>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 400, boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, color: '#1e293b', marginBottom: 24 }}>{holiday ? '휴일 수정' : '휴일 등록'}</h2>
        <form onSubmit={e => { e.preventDefault(); mutation.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Field label="날짜">
            <input type="date" value={form.holidayDate} onChange={e => set('holidayDate', e.target.value)} required style={inputStyle} />
          </Field>
          <Field label="명칭">
            <input value={form.name} onChange={e => set('name', e.target.value)} required style={inputStyle} placeholder="예: 임시공휴일" />
          </Field>
          <Field label="유형">
            <select value={form.holidayType} onChange={e => set('holidayType', e.target.value as HolidayType)} style={inputStyle}>
              {MANUAL_HOLIDAY_TYPES.map(t => (
                <option key={t} value={t}>{HOLIDAY_TYPE_LABEL[t]}</option>
              ))}
            </select>
          </Field>
          <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
            <button type="button" onClick={onClose} style={cancelBtnStyle}>취소</button>
            <button type="submit" disabled={mutation.isPending} style={primaryBtnStyle}>
              {mutation.isPending ? '저장 중...' : '저장'}
            </button>
          </div>
        </form>
      </div>
    </Overlay>
  )
}

// 연도 공휴일 프리셋(고정 법정공휴일)과 월별 주말 일괄 등록이 공유하는 체크리스트 모달.
// 후보 목록만 다르고 나머지 동작(선택 해제, 중복 스킵 결과 표시)은 동일하다.
function BulkRegisterModal({
  title, description, candidates, holidayType, isLoading, onClose,
}: {
  title: string
  description: string
  candidates: HolidayPreset[]
  holidayType: HolidayType
  isLoading?: boolean
  onClose: () => void
}) {
  const qc = useQueryClient()
  const [unchecked, setUnchecked] = useState<Record<string, boolean>>({})

  const mutation = useMutation({
    mutationFn: () => bulkCreateHolidays(
      candidates
        .filter(c => !unchecked[c.holidayDate])
        .map(c => ({ holidayDate: c.holidayDate, name: c.name, holidayType })),
    ),
    onSuccess: (res) => {
      const { created, skipped } = res.data.data
      toast.success(`${created}건 등록되었습니다.${skipped > 0 ? ` (이미 등록된 ${skipped}건은 건너뜀)` : ''}`)
      qc.invalidateQueries({ queryKey: ['holidays'] })
      onClose()
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '오류가 발생했습니다.'),
  })

  return (
    <Overlay>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 420, maxHeight: '80vh', overflowY: 'auto', boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, color: '#1e293b', marginBottom: 8 }}>{title}</h2>
        <p style={{ fontSize: 12, color: '#94a3b8', marginBottom: 16, lineHeight: 1.5 }}>{description}</p>
        {isLoading ? (
          <p style={{ color: '#64748b' }}>로딩 중...</p>
        ) : candidates.length === 0 ? (
          <p style={{ color: '#64748b' }}>등록할 항목이 없습니다.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 20 }}>
            {candidates.map(c => (
              <label key={c.holidayDate} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, color: '#374151', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={!unchecked[c.holidayDate]}
                  onChange={e => setUnchecked(prev => ({ ...prev, [c.holidayDate]: !e.target.checked }))}
                />
                <span style={{ color: '#94a3b8', width: 92 }}>{c.holidayDate}</span>
                <span>{c.name}</span>
              </label>
            ))}
          </div>
        )}
        <div style={{ display: 'flex', gap: 10 }}>
          <button type="button" onClick={onClose} style={cancelBtnStyle}>취소</button>
          <button
            type="button"
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || candidates.length === 0}
            style={primaryBtnStyle}
          >
            {mutation.isPending ? '등록 중...' : '선택한 항목 등록'}
          </button>
        </div>
      </div>
    </Overlay>
  )
}

function YearPresetModal({ year, onClose }: { year: number; onClose: () => void }) {
  const { data: presets = [], isLoading } = useQuery({
    queryKey: ['holiday-presets', year],
    queryFn: () => getHolidayPresets(year).then(r => r.data.data),
  })

  return (
    <BulkRegisterModal
      title={`${year}년 공휴일 일괄 등록`}
      description="매년 날짜가 고정된 법정공휴일만 자동으로 채워집니다. 설날·추석·부처님오신날처럼 음력 기준이라 해마다 날짜가 달라지는 공휴일과 대체공휴일은 직접 등록해주세요."
      candidates={presets}
      holidayType="PUBLIC"
      isLoading={isLoading}
      onClose={onClose}
    />
  )
}

function WeekendPresetModal({ year, month, onClose }: { year: number; month: number; onClose: () => void }) {
  const daysInMonth = new Date(year, month, 0).getDate()
  const candidates: HolidayPreset[] = Array.from({ length: daysInMonth }, (_, i) => i + 1)
    .map(day => ({ day, weekday: weekdayOf(year, month, day) }))
    .filter(d => d.weekday === 0 || d.weekday === 6)
    .map(d => ({
      holidayDate: `${year}-${pad2(month)}-${pad2(d.day)}`,
      name: d.weekday === 0 ? '일요일' : '토요일',
    }))

  return (
    <BulkRegisterModal
      title={`${year}년 ${month}월 주말 일괄 등록`}
      description="이 달의 토요일·일요일을 실제 휴일로 등록합니다. 등록하지 않아도 달력과 목록에는 자동으로 표시되지만, 명시적으로 등록하면 다른 휴일처럼 유형을 지정하고 수정·삭제할 수 있습니다."
      candidates={candidates}
      holidayType="WEEKEND"
      onClose={onClose}
    />
  )
}

// 휴가 신청 엑셀 일괄 업로드. 이름으로 사용자를 찾아 leave_requests에 바로 승인 상태로 등록한다.
function LeaveBulkImportModal({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [result, setResult] = useState<BulkLeaveImportResponse | null>(null)

  const handleUpload = async () => {
    if (!file) { toast.error('엑셀 파일을 선택해주세요.'); return }
    setUploading(true)
    setResult(null)
    try {
      const res = await bulkUploadLeaveRequests(file)
      setResult(res.data.data)
      qc.invalidateQueries({ queryKey: ['leave-calendar'] })
      if (res.data.data.failureCount === 0) {
        toast.success(`${res.data.data.successCount}건 등록되었습니다.`)
      } else {
        toast.error(`성공 ${res.data.data.successCount}건, 실패 ${res.data.data.failureCount}건`)
      }
    } catch (err: any) {
      toast.error(err?.response?.data?.message ?? '업로드 실패')
    } finally {
      setUploading(false)
    }
  }

  return (
    <Overlay>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 560, maxHeight: '85vh', overflowY: 'auto', boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 8, color: '#1e293b' }}>휴가 일괄 업로드</h2>
        <p style={{ fontSize: 13, color: '#64748b', marginBottom: 20, lineHeight: 1.6 }}>
          엑셀 헤더에 "이름, 구분, 시작일, 시작시간, 종료일, 종료시간, 사유"가 있어야 합니다.
          이름으로 직원을 찾아 등록하며, 등록된 건은 곧바로 승인 상태로 저장됩니다.
          "삭제여부"가 표시된 행은 건너뜁니다.
        </p>

        <button
          type="button"
          onClick={() => downloadLeaveBulkImportTemplate()}
          style={{ padding: '8px 14px', background: '#f1f5f9', color: '#374151', border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 600, marginBottom: 20 }}
        >
          템플릿 다운로드
        </button>

        <div style={{ marginBottom: 20 }}>
          <input
            type="file"
            accept=".xlsx,.xls"
            onChange={e => setFile(e.target.files?.[0] ?? null)}
            style={{ fontSize: 13 }}
          />
        </div>

        {result && (
          <div style={{ marginBottom: 20, background: '#f8fafc', borderRadius: 8, padding: 14 }}>
            <p style={{ fontSize: 13, fontWeight: 600, color: '#1e293b', marginBottom: 10 }}>
              총 {result.totalRows}건 중 성공 <span style={{ color: '#16a34a' }}>{result.successCount}</span>건,
              실패 <span style={{ color: '#ef4444' }}>{result.failureCount}</span>건
            </p>
            <div style={{ maxHeight: 220, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 4 }}>
              {result.results.map(r => (
                <div key={r.rowNumber} style={{ fontSize: 12, color: r.success ? '#16a34a' : '#ef4444' }}>
                  {r.rowNumber}행 {r.name || '(이름 없음)'} — {r.message}
                </div>
              ))}
            </div>
          </div>
        )}

        <div style={{ display: 'flex', gap: 10 }}>
          <button type="button" onClick={onClose} style={cancelBtnStyle}>닫기</button>
          <button type="button" onClick={handleUpload} disabled={uploading || !file} style={{
            ...primaryBtnStyle, flex: 1, opacity: (uploading || !file) ? 0.6 : 1,
          }}>{uploading ? '업로드 중...' : '업로드'}</button>
        </div>
      </div>
    </Overlay>
  )
}

// 달력 셀 클릭 시 그 날짜에 걸친 휴가 전체 목록을 보여주는 팝업.
function DayLeaveListModal({ date, leaves, onClose }: { date: string; leaves: LeaveRequest[]; onClose: () => void }) {
  return (
    <Overlay>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 480, maxHeight: '80vh', overflowY: 'auto', boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, color: '#1e293b', marginBottom: 4 }}>{date} 휴가 목록</h2>
        <p style={{ fontSize: 12, color: '#94a3b8', marginBottom: 16 }}>총 {leaves.length}건</p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {leaves.map(l => (
            <div key={l.id} style={{ border: '1px solid #e2e8f0', borderRadius: 8, padding: 12 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                <span style={{ fontWeight: 600, fontSize: 14, color: '#1e293b' }}>{l.requesterName ?? `#${l.requesterId}`}</span>
                <span style={{ padding: '2px 8px', borderRadius: 4, fontSize: 11, fontWeight: 600, background: '#dbeafe', color: '#1d4ed8' }}>
                  {LEAVE_TYPE_LABEL[l.requestType]}
                </span>
              </div>
              <div style={{ fontSize: 12, color: '#64748b' }}>{formatLeaveRange(l)}</div>
              {l.reason && <div style={{ fontSize: 12, color: '#374151', marginTop: 4 }}>{l.reason}</div>}
            </div>
          ))}
        </div>
        <div style={{ marginTop: 20 }}>
          <button type="button" onClick={onClose} style={{ ...cancelBtnStyle, width: '100%' }}>닫기</button>
        </div>
      </div>
    </Overlay>
  )
}

export default function HolidaysPage() {
  const qc = useQueryClient()
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [modal, setModal] = useState<Holiday | null | 'new'>(null)
  const [yearPresetModalOpen, setYearPresetModalOpen] = useState(false)
  const [weekendPresetModalOpen, setWeekendPresetModalOpen] = useState(false)
  const [leaveBulkImportOpen, setLeaveBulkImportOpen] = useState(false)
  const [dayLeaveModal, setDayLeaveModal] = useState<{ date: string; leaves: LeaveRequest[] } | null>(null)
  const { isActionEnabled } = usePermissions()
  const canCreate = isActionEnabled('holidays', 'CREATE')
  const canBulkCreate = isActionEnabled('holidays', 'BULK_CREATE')
  const canEdit = isActionEnabled('holidays', 'EDIT')
  const role = useAuthStore(s => s.role)
  // 삭제는 권한관리에서 조정 가능한 일반 권한이 아니라 시스템관리자로 고정한다(서버도 동일 기준으로 검증).
  const canDelete = role === 'SYSTEM_ADMIN'

  const { data: holidays = [], isLoading: holidaysLoading } = useQuery({
    queryKey: ['holidays'],
    queryFn: () => getHolidays().then(r => r.data.data),
  })
  const { data: leaves = [] } = useQuery({
    queryKey: ['leave-calendar', year, month],
    queryFn: () => getApprovedLeaveCalendar(year, month).then(r => r.data.data),
  })

  const del = useMutation({
    mutationFn: (id: number) => deleteHoliday(id),
    onSuccess: () => { toast.success('삭제되었습니다.'); qc.invalidateQueries({ queryKey: ['holidays'] }) },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '삭제 실패'),
  })

  const holidaysByDate = new Map(holidays.map(h => [h.holidayDate, h]))
  const monthPrefix = `${year}-${pad2(month)}`

  const daysInMonth = new Date(year, month, 0).getDate()
  const startWeekday = weekdayOf(year, month, 1)
  const cells: (number | null)[] = [
    ...Array(startWeekday).fill(null),
    ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
  ]

  // 토요일·일요일은 실제 등록 없이도 항상 휴일 목록에 자동으로 나타난다.
  // 이미 그 날짜에 등록된 휴일(공휴일·대체공휴일·회사휴일·주말)이 있으면 그걸 우선하고,
  // 없을 때만 "주말" 항목으로 채운다 — 이 경우 수정·삭제는 불가(실제 DB row가 아니므로).
  const monthListItems = Array.from({ length: daysInMonth }, (_, i) => i + 1).map(day => {
    const dateStr = `${monthPrefix}-${pad2(day)}`
    const holiday = holidaysByDate.get(dateStr)
    if (holiday) {
      return { date: dateStr, name: holiday.name, typeLabel: HOLIDAY_TYPE_LABEL[holiday.holidayType], color: HOLIDAY_TYPE_COLOR[holiday.holidayType], holiday }
    }
    const weekday = weekdayOf(year, month, day)
    if (weekday === 0 || weekday === 6) {
      return { date: dateStr, name: weekday === 0 ? '일요일' : '토요일', typeLabel: HOLIDAY_TYPE_LABEL.WEEKEND, color: HOLIDAY_TYPE_COLOR.WEEKEND, holiday: undefined }
    }
    return null
  }).filter((item): item is NonNullable<typeof item> => item !== null)

  const leavesForDay = (day: number) => {
    const dateStr = `${monthPrefix}-${pad2(day)}`
    return leaves.filter(l => toKstDate(l.startAt) <= dateStr && toKstDate(l.endAt) >= dateStr)
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, color: '#1e293b' }}>휴일/휴가 관리</h1>
        <div style={{ display: 'flex', gap: 8 }}>
          {canBulkCreate && <button onClick={() => setYearPresetModalOpen(true)} style={cancelBtnStyle}>연도 공휴일 일괄 등록</button>}
          {canBulkCreate && <button onClick={() => setWeekendPresetModalOpen(true)} style={cancelBtnStyle}>월별 주말 일괄 등록</button>}
          {canBulkCreate && <button onClick={() => setLeaveBulkImportOpen(true)} style={cancelBtnStyle}>휴가 일괄업로드</button>}
          {canCreate && <button onClick={() => setModal('new')} style={primaryBtnStyle}>+ 휴일 등록</button>}
        </div>
      </div>

      <div style={{ display: 'flex', gap: 8, marginBottom: 16, alignItems: 'center', flexWrap: 'wrap' }}>
        <select value={year} onChange={e => setYear(Number(e.target.value))} style={filterInputStyle}>
          {[year - 1, year, year + 1].map(y => <option key={y} value={y}>{y}년</option>)}
        </select>
        <select value={month} onChange={e => setMonth(Number(e.target.value))} style={filterInputStyle}>
          {Array.from({ length: 12 }, (_, i) => i + 1).map(m => <option key={m} value={m}>{m}월</option>)}
        </select>
        <div style={{ display: 'flex', gap: 14, marginLeft: 16, fontSize: 12, color: '#64748b' }}>
          <LegendDot color={HOLIDAY_TYPE_COLOR.PUBLIC} label="공휴일" />
          <LegendDot color={HOLIDAY_TYPE_COLOR.SUBSTITUTE} label="대체공휴일" />
          <LegendDot color={HOLIDAY_TYPE_COLOR.COMPANY} label="회사휴일" />
          <LegendDot color={HOLIDAY_TYPE_COLOR.WEEKEND} label="주말" />
          <LegendDot color="#2563eb" label="직원 휴가" />
        </div>
      </div>

      <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', padding: 16, marginBottom: 24 }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 4 }}>
          {['일', '월', '화', '수', '목', '금', '토'].map(d => (
            <div key={d} style={{ textAlign: 'center', fontSize: 12, fontWeight: 600, color: '#94a3b8', padding: '4px 0' }}>{d}</div>
          ))}
          {cells.map((day, i) => {
            if (day === null) return <div key={`empty-${i}`} />
            const dateStr = `${monthPrefix}-${pad2(day)}`
            const holiday = holidaysByDate.get(dateStr)
            const dayLeaves = leavesForDay(day)
            const weekday = weekdayOf(year, month, day)
            const isWeekend = weekday === 0 || weekday === 6
            const cellColor = holiday ? HOLIDAY_TYPE_COLOR[holiday.holidayType] : isWeekend ? HOLIDAY_TYPE_COLOR.WEEKEND : undefined
            return (
              <div key={dateStr} style={{
                minHeight: 86, border: '1px solid #f1f5f9', borderRadius: 8, padding: 6,
                background: cellColor ? cellColor + '10' : '#fff',
              }}>
                <div style={{
                  fontSize: 12, fontWeight: 600,
                  color: holiday ? HOLIDAY_TYPE_COLOR[holiday.holidayType] : weekday === 0 ? '#ef4444' : weekday === 6 ? '#2563eb' : '#374151',
                }}>{day}</div>
                {holiday && (
                  <div
                    onClick={() => setModal(holiday)}
                    title="클릭하여 수정"
                    style={{
                      marginTop: 2, fontSize: 11, fontWeight: 600, color: '#fff',
                      background: HOLIDAY_TYPE_COLOR[holiday.holidayType], borderRadius: 4,
                      padding: '1px 4px', cursor: 'pointer', display: 'inline-block',
                    }}
                  >{holiday.name}</div>
                )}
                {!holiday && isWeekend && (
                  <div style={{
                    marginTop: 2, fontSize: 11, fontWeight: 600, color: '#fff',
                    background: HOLIDAY_TYPE_COLOR.WEEKEND, borderRadius: 4,
                    padding: '1px 4px', display: 'inline-block',
                  }}>{weekday === 0 ? '일요일' : '토요일'}</div>
                )}
                {dayLeaves.length > 0 && (
                  <div
                    onClick={() => setDayLeaveModal({ date: dateStr, leaves: dayLeaves })}
                    title="클릭하여 전체 휴가 목록 보기"
                    style={{ cursor: 'pointer' }}
                  >
                    {dayLeaves.slice(0, DAY_CELL_MAX_LEAVES).map(l => (
                      <div
                        key={l.id}
                        style={{
                          marginTop: 2, fontSize: 10, color: '#1d4ed8', background: '#dbeafe', borderRadius: 4,
                          padding: '1px 4px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                        }}
                      >{l.requesterName ?? `#${l.requesterId}`} · {LEAVE_TYPE_LABEL[l.requestType]}</div>
                    ))}
                    {dayLeaves.length > DAY_CELL_MAX_LEAVES && (
                      <div style={{ marginTop: 2, fontSize: 10, fontWeight: 600, color: '#2563eb' }}>
                        +{dayLeaves.length - DAY_CELL_MAX_LEAVES}건 더보기
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      </div>

      <h2 style={{ fontSize: 16, fontWeight: 700, color: '#1e293b', marginBottom: 12 }}>{year}년 {month}월 휴일 목록</h2>
      <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', overflow: 'hidden' }}>
        {holidaysLoading ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>로딩 중...</div>
        ) : monthListItems.length === 0 ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>등록된 휴일이 없습니다.</div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#f8fafc' }}>
                {['날짜', '명칭', '유형', ''].map(h => <th key={h} style={thStyle}>{h}</th>)}
              </tr>
            </thead>
            <tbody>
              {monthListItems.map(item => (
                <tr key={item.date} style={{ borderBottom: '1px solid #f1f5f9' }}>
                  <td style={tdStyle}>{item.date}</td>
                  <td style={tdStyle}>{item.name}</td>
                  <td style={tdStyle}>
                    <span style={{
                      padding: '2px 8px', borderRadius: 4, fontSize: 12, fontWeight: 600,
                      background: item.color + '20', color: item.color,
                    }}>{item.typeLabel}</span>
                  </td>
                  <td style={{ ...tdStyle, textAlign: 'right', whiteSpace: 'nowrap' }}>
                    {item.holiday ? (
                      <>
                        {canEdit && <button onClick={() => setModal(item.holiday!)} style={editBtnStyle}>수정</button>}{' '}
                        {canDelete && (
                          <button onClick={() => { if (confirm('삭제하시겠습니까?')) del.mutate(item.holiday!.id) }} style={deleteBtnStyle}>삭제</button>
                        )}
                      </>
                    ) : (
                      <span style={{ fontSize: 12, color: '#94a3b8' }}>자동 표시</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {modal === 'new' && <HolidayModal onClose={() => setModal(null)} />}
      {modal && modal !== 'new' && <HolidayModal holiday={modal as Holiday} onClose={() => setModal(null)} />}
      {yearPresetModalOpen && <YearPresetModal year={year} onClose={() => setYearPresetModalOpen(false)} />}
      {weekendPresetModalOpen && <WeekendPresetModal year={year} month={month} onClose={() => setWeekendPresetModalOpen(false)} />}
      {leaveBulkImportOpen && <LeaveBulkImportModal onClose={() => setLeaveBulkImportOpen(false)} />}
      {dayLeaveModal && (
        <DayLeaveListModal
          date={dayLeaveModal.date}
          leaves={dayLeaveModal.leaves}
          onClose={() => setDayLeaveModal(null)}
        />
      )}
    </div>
  )
}

function LegendDot({ color, label }: { color: string; label: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
      <span style={{ width: 8, height: 8, borderRadius: '50%', background: color, display: 'inline-block' }} />
      {label}
    </div>
  )
}

function Overlay({ children }: { children: React.ReactNode }) {
  return <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100 }}>{children}</div>
}
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: 'block', fontSize: 13, fontWeight: 500, color: '#374151', marginBottom: 4 }}>{label}</label>{children}</div>
}
const inputStyle: React.CSSProperties = { width: '100%', padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' }
const filterInputStyle: React.CSSProperties = { padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 13 }
const primaryBtnStyle: React.CSSProperties = { padding: '8px 18px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontWeight: 600, fontSize: 14 }
const cancelBtnStyle: React.CSSProperties = { padding: '8px 18px', border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontSize: 14, background: '#fff', color: '#374151', fontWeight: 600 }
const editBtnStyle: React.CSSProperties = { padding: '4px 10px', fontSize: 12, color: '#2563eb', border: '1px solid #bfdbfe', borderRadius: 6, background: '#fff', cursor: 'pointer' }
const deleteBtnStyle: React.CSSProperties = { padding: '4px 10px', fontSize: 12, color: '#ef4444', border: '1px solid #fca5a5', borderRadius: 6, background: '#fff', cursor: 'pointer' }
const thStyle: React.CSSProperties = { padding: '12px 16px', textAlign: 'left', fontSize: 12, fontWeight: 600, color: '#64748b', borderBottom: '1px solid #e2e8f0' }
const tdStyle: React.CSSProperties = { padding: '12px 16px', fontSize: 14, color: '#374151' }
