import { useMemo, useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import listPlugin from '@fullcalendar/list'
import interactionPlugin from '@fullcalendar/interaction'
import type { EventClickArg, EventInput, DateSelectArg, DayCellMountArg, DayHeaderMountArg } from '@fullcalendar/core'
import { getCalendarEvents, createCalendarEvent, updateCalendarEvent, deleteCalendarEvent } from '@/api/calendarEvents'
import type { CalendarEventPayload } from '@/api/calendarEvents'
import { getHolidays } from '@/api/holidays'
import type { CalendarEvent, CalendarEventCategory, CalendarEventVisibility, HolidayType } from '@/types'
import { useAuthStore } from '@/store/authStore'
import toast from 'react-hot-toast'

// 휴일/휴가 관리 화면과 동일한 유형·색상 체계로 공휴일·대체공휴일·회사휴일을 캘린더에 함께 표기한다.
const HOLIDAY_TYPE_LABEL: Record<HolidayType, string> = {
  PUBLIC: '공휴일', SUBSTITUTE: '대체공휴일', COMPANY: '회사휴일', WEEKEND: '주말',
}
const HOLIDAY_TYPE_COLOR: Record<HolidayType, string> = {
  PUBLIC: '#ef4444', SUBSTITUTE: '#f97316', COMPANY: '#8b5cf6', WEEKEND: '#64748b',
}
const SUNDAY_COLOR = '#ef4444'
const SATURDAY_COLOR = '#2563eb'

// 등록/수정/삭제는 권한레벨이 이 값에 속한 계정만 가능(승인함 승인/반려 권한과 동일한 기준).
// 서버(CalendarEventService)도 동일 기준으로 검증하므로 이 배열은 화면 표시용일 뿐이다.
const SCHEDULE_ADMIN_LEVELS = ['SYSADMIN', 'HRADMIN', 'PRESIDENT']

const CATEGORY_LABEL: Record<CalendarEventCategory, string> = {
  MEETING: '회의', EVENT: '행사', NOTICE: '공지', ETC: '기타',
}
const CATEGORY_COLOR: Record<CalendarEventCategory, string> = {
  MEETING: '#2563eb', EVENT: '#16a34a', NOTICE: '#f59e0b', ETC: '#64748b',
}
const VISIBILITY_LABEL: Record<CalendarEventVisibility, string> = {
  ALL: '전체', PERSONAL: '개인',
}

function LegendDot({ color, label }: { color: string; label: string }) {
  return (
    <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
      <span style={{ width: 8, height: 8, borderRadius: '50%', background: color, display: 'inline-block' }} />
      {label}
    </span>
  )
}

function toLocalInputValue(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export default function SchedulesPage() {
  const level = useAuthStore(s => s.level)
  const userId = useAuthStore(s => s.userId)
  // 권한레벨 계정은 모든 일정(전체+개인)을 등록·수정·삭제할 수 있다.
  const isScheduleAdmin = !!level && SCHEDULE_ADMIN_LEVELS.includes(level)
  // 그 외 사용자는 본인의 개인 일정만 등록·수정·삭제할 수 있다(신규 등록은 항상 허용, 기존 일정은 본인 개인 일정일 때만).
  const canEditEvent = (e?: CalendarEvent) =>
    isScheduleAdmin || !e || (e.visibility === 'PERSONAL' && e.targetUserId === userId)

  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const monthRange = (y: number, m: number) => ({
    from: `${y}-${String(m).padStart(2, '0')}-01`,
    to: new Date(y, m, 0).toISOString().slice(0, 10),
  })
  const [queryRange, setQueryRange] = useState(monthRange(year, month))
  const [searchText, setSearchText] = useState('')
  const [editTarget, setEditTarget] = useState<CalendarEvent | 'new' | null>(null)
  const [newEventDefaults, setNewEventDefaults] = useState<{ start?: string; end?: string; allDay?: boolean }>({})
  const calendarRef = useRef<FullCalendar>(null)

  const { data: events = [], isLoading } = useQuery({
    queryKey: ['calendar-events', queryRange.from, queryRange.to],
    queryFn: () => getCalendarEvents(queryRange.from, queryRange.to).then(r => r.data.data),
  })

  // 등록된 휴일은 기간과 무관하게 전체를 한 번만 불러와 달력에 표시한다(휴일/휴가관리 화면과 동일한 데이터).
  const { data: holidays = [] } = useQuery({
    queryKey: ['holidays'],
    queryFn: () => getHolidays().then(r => r.data.data),
  })

  const filteredEvents = useMemo(() => {
    const q = searchText.trim().toLowerCase()
    if (!q) return events
    return events.filter(e => e.title.toLowerCase().includes(q) || (e.description ?? '').toLowerCase().includes(q))
  }, [events, searchText])

  const fcEvents: EventInput[] = filteredEvents.map(e => ({
    id: String(e.id),
    title: e.visibility === 'PERSONAL' ? `[개인] ${e.title}` : e.title,
    start: e.startAt,
    end: e.endAt,
    allDay: e.allDay,
    backgroundColor: e.color || CATEGORY_COLOR[e.category],
    borderColor: e.color || CATEGORY_COLOR[e.category],
  }))

  // 주말은 이미 요일 색상으로 표시하므로, 별도 등록된 "토요일/일요일" 휴일 항목은 배지로 중복 표시하지 않는다.
  const holidayEvents: EventInput[] = holidays.filter(h => h.holidayType !== 'WEEKEND').map(h => ({
    id: `holiday-${h.id}`,
    title: h.name,
    start: h.holidayDate,
    allDay: true,
    backgroundColor: HOLIDAY_TYPE_COLOR[h.holidayType],
    borderColor: HOLIDAY_TYPE_COLOR[h.holidayType],
    editable: false,
    extendedProps: { isHoliday: true, holidayType: h.holidayType },
  }))

  const handleSearch = () => {
    setQueryRange(monthRange(year, month))
    calendarRef.current?.getApi().gotoDate(new Date(year, month - 1, 1))
  }

  const handleEventClick = (arg: EventClickArg) => {
    if (arg.event.extendedProps.isHoliday) {
      const holidayType = arg.event.extendedProps.holidayType as HolidayType
      toast(`${arg.event.title} (${HOLIDAY_TYPE_LABEL[holidayType]})`)
      return
    }
    const found = events.find(e => String(e.id) === arg.event.id)
    if (found) setEditTarget(found)
  }

  // 토요일은 파란색, 일요일은 빨간색으로 표기한다(휴일/휴가관리 화면 달력과 동일한 색상 규칙).
  const styleWeekendText = (el: HTMLElement, dow: number) => {
    if (dow !== 0 && dow !== 6) return
    const target = (el.querySelector('.fc-daygrid-day-number, .fc-col-header-cell-cushion') as HTMLElement | null) ?? el
    target.style.color = dow === 0 ? SUNDAY_COLOR : SATURDAY_COLOR
  }
  const handleDayCellMount = (arg: DayCellMountArg) => styleWeekendText(arg.el, arg.date.getDay())
  const handleDayHeaderMount = (arg: DayHeaderMountArg) => styleWeekendText(arg.el, arg.date.getDay())

  const handleDateSelect = (info: DateSelectArg) => {
    setNewEventDefaults({ start: info.startStr, end: info.endStr, allDay: info.allDay })
    setEditTarget('new')
  }

  const handleExportCsv = () => {
    const header = '제목,구분,공개범위,시작,종료,종일,장소,대상자'
    const rows = filteredEvents.map(e => [
      e.title, CATEGORY_LABEL[e.category], VISIBILITY_LABEL[e.visibility],
      e.startAt, e.endAt, e.allDay ? 'Y' : 'N', e.location ?? '', e.targetUserName ?? '',
    ].map(v => `"${String(v).replace(/"/g, '""')}"`).join(','))
    const csv = [header, ...rows].join('\n')
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = `calendar_events_${queryRange.from}_${queryRange.to}.csv`; a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div>
      <h1 style={{ fontSize: 22, fontWeight: 700, color: '#1e293b', marginBottom: 24 }}>일정관리</h1>

      <div style={{
        background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)',
        padding: 20, marginBottom: 20, display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap',
      }}>
        <span style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>기간</span>
        <select value={year} onChange={e => setYear(Number(e.target.value))} style={filterInputStyle}>
          {[year - 1, year, year + 1].map(y => <option key={y} value={y}>{y}년</option>)}
        </select>
        <select value={month} onChange={e => setMonth(Number(e.target.value))} style={filterInputStyle}>
          {Array.from({ length: 12 }, (_, i) => i + 1).map(m => <option key={m} value={m}>{m}월</option>)}
        </select>
        <input
          value={searchText} onChange={e => setSearchText(e.target.value)}
          placeholder="제목·내용 검색" style={{ ...filterInputStyle, flex: 1, minWidth: 160 }}
        />
        <button onClick={handleSearch} style={primaryBtnStyle}>검색</button>
      </div>

      <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', padding: 20 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, marginBottom: 12, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', gap: 14, fontSize: 12, color: '#64748b' }}>
            <LegendDot color={HOLIDAY_TYPE_COLOR.PUBLIC} label="공휴일" />
            <LegendDot color={HOLIDAY_TYPE_COLOR.SUBSTITUTE} label="대체공휴일" />
            <LegendDot color={HOLIDAY_TYPE_COLOR.COMPANY} label="회사휴일" />
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button onClick={handleExportCsv} style={secondaryBtnStyle}>엑셀</button>
            <button onClick={() => { setNewEventDefaults({}); setEditTarget('new') }} style={primaryBtnStyle}>일정등록</button>
          </div>
        </div>
        {isLoading ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>로딩 중...</div>
        ) : (
          <FullCalendar
            ref={calendarRef}
            plugins={[dayGridPlugin, timeGridPlugin, listPlugin, interactionPlugin]}
            initialView="dayGridMonth"
            headerToolbar={{
              left: 'prev,next today',
              center: 'title',
              right: 'dayGridMonth,timeGridWeek,timeGridDay,listMonth',
            }}
            buttonText={{ today: 'today', month: '월', week: '주', day: '일', list: '일정목록' }}
            height="auto"
            events={[...fcEvents, ...holidayEvents]}
            selectable
            select={handleDateSelect}
            eventClick={handleEventClick}
            dayCellDidMount={handleDayCellMount}
            dayHeaderDidMount={handleDayHeaderMount}
          />
        )}
      </div>

      {editTarget && (
        <EventModal
          event={editTarget === 'new' ? undefined : editTarget}
          defaults={newEventDefaults}
          canManage={canEditEvent(editTarget === 'new' ? undefined : editTarget)}
          isScheduleAdmin={isScheduleAdmin}
          onClose={() => setEditTarget(null)}
        />
      )}
    </div>
  )
}

function EventModal({ event, defaults, canManage, isScheduleAdmin, onClose }: {
  event?: CalendarEvent
  defaults: { start?: string; end?: string; allDay?: boolean }
  canManage: boolean
  isScheduleAdmin: boolean
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [title, setTitle] = useState(event?.title ?? '')
  const [startAt, setStartAt] = useState(event ? toLocalInputValue(event.startAt) : (defaults.start ?? ''))
  const [endAt, setEndAt] = useState(event ? toLocalInputValue(event.endAt) : (defaults.end ?? ''))
  const [allDay, setAllDay] = useState(event?.allDay ?? defaults.allDay ?? false)
  const [category, setCategory] = useState<CalendarEventCategory>(event?.category ?? 'MEETING')
  const [visibility, setVisibility] = useState<CalendarEventVisibility>(
    event?.visibility ?? (isScheduleAdmin ? 'ALL' : 'PERSONAL')
  )
  const [location, setLocation] = useState(event?.location ?? '')
  const [color, setColor] = useState(event?.color ?? '#2563eb')
  const [description, setDescription] = useState(event?.description ?? '')

  const readOnly = !canManage
  // 권한레벨 계정이 아니면 공개범위를 "개인"으로만 등록할 수 있다(선택창 자체를 잠근다).
  const canChooseVisibility = isScheduleAdmin && !readOnly

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!title.trim()) throw new Error('제목을 입력해주세요.')
      if (!startAt || !endAt) throw new Error('시작·종료 일시를 입력해주세요.')
      const payload: CalendarEventPayload = {
        title,
        startAt: new Date(startAt).toISOString(),
        endAt: new Date(endAt).toISOString(),
        allDay,
        description: description || undefined,
        location: location || undefined,
        color,
        category,
        visibility,
      }
      return event ? updateCalendarEvent(event.id, payload) : createCalendarEvent(payload)
    },
    onSuccess: () => {
      toast.success(event ? '일정이 수정되었습니다.' : '일정이 등록되었습니다.')
      queryClient.invalidateQueries({ queryKey: ['calendar-events'] })
      onClose()
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? e?.message ?? '저장 실패'),
  })

  const deleteMutation = useMutation({
    mutationFn: () => deleteCalendarEvent(event!.id),
    onSuccess: () => {
      toast.success('일정이 삭제되었습니다.')
      queryClient.invalidateQueries({ queryKey: ['calendar-events'] })
      onClose()
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '삭제 실패'),
  })

  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100,
    }}>
      <div style={{
        background: '#fff', borderRadius: 12, padding: 32, width: 480,
        maxHeight: '85vh', overflowY: 'auto', boxShadow: '0 8px 32px rgba(0,0,0,0.12)',
      }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 20, color: '#1e293b' }}>
          {event ? (readOnly ? '일정 상세' : '일정 수정') : '일정 등록'}
        </h2>
        <form onSubmit={e => { e.preventDefault(); saveMutation.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={labelStyle}>제목</label>
            <input value={title} onChange={e => setTitle(e.target.value)} required disabled={readOnly} style={inputStyle} />
          </div>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
            <input type="checkbox" checked={allDay} onChange={e => setAllDay(e.target.checked)} disabled={readOnly} /> 종일
          </label>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <div>
              <label style={labelStyle}>시작</label>
              <input
                type={allDay ? 'date' : 'datetime-local'}
                value={allDay ? startAt.slice(0, 10) : startAt}
                onChange={e => setStartAt(allDay ? `${e.target.value}T00:00` : e.target.value)}
                required disabled={readOnly} style={inputStyle}
              />
            </div>
            <div>
              <label style={labelStyle}>종료</label>
              <input
                type={allDay ? 'date' : 'datetime-local'}
                value={allDay ? endAt.slice(0, 10) : endAt}
                onChange={e => setEndAt(allDay ? `${e.target.value}T23:59` : e.target.value)}
                required disabled={readOnly} style={inputStyle}
              />
            </div>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <div>
              <label style={labelStyle}>구분</label>
              <select value={category} onChange={e => setCategory(e.target.value as CalendarEventCategory)} disabled={readOnly} style={inputStyle}>
                {(Object.entries(CATEGORY_LABEL) as [CalendarEventCategory, string][]).map(([v, l]) => (
                  <option key={v} value={v}>{l}</option>
                ))}
              </select>
            </div>
            <div>
              <label style={labelStyle}>공개 범위</label>
              <select value={visibility} onChange={e => setVisibility(e.target.value as CalendarEventVisibility)} disabled={!canChooseVisibility} style={inputStyle}>
                {((isScheduleAdmin ? Object.entries(VISIBILITY_LABEL) : [['PERSONAL', VISIBILITY_LABEL.PERSONAL]]) as [CalendarEventVisibility, string][]).map(([v, l]) => (
                  <option key={v} value={v}>{l}</option>
                ))}
              </select>
              {visibility === 'PERSONAL' && (
                <p style={{ fontSize: 11, color: '#94a3b8', marginTop: 4 }}>본인에게만 보이는 일정으로 등록됩니다.</p>
              )}
            </div>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 80px', gap: 10 }}>
            <div>
              <label style={labelStyle}>장소</label>
              <input value={location} onChange={e => setLocation(e.target.value)} disabled={readOnly} style={inputStyle} />
            </div>
            <div>
              <label style={labelStyle}>색상</label>
              <input type="color" value={color} onChange={e => setColor(e.target.value)} disabled={readOnly} style={{ ...inputStyle, padding: 2, height: 38 }} />
            </div>
          </div>
          <div>
            <label style={labelStyle}>내용</label>
            <textarea value={description} onChange={e => setDescription(e.target.value)} disabled={readOnly} rows={3} style={{ ...inputStyle, resize: 'vertical' }} />
          </div>

          <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
            <button type="button" onClick={onClose} style={cancelBtnStyle}>{readOnly ? '닫기' : '취소'}</button>
            {!readOnly && event && (
              <button
                type="button"
                onClick={() => { if (confirm('삭제하시겠습니까?')) deleteMutation.mutate() }}
                disabled={deleteMutation.isPending}
                style={deleteBtnStyle}
              >삭제</button>
            )}
            {!readOnly && (
              <button type="submit" disabled={saveMutation.isPending} style={{ ...primaryBtnStyle, flex: 1 }}>
                {saveMutation.isPending ? '저장 중...' : '저장'}
              </button>
            )}
          </div>
        </form>
      </div>
    </div>
  )
}

const filterInputStyle: React.CSSProperties = { padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 13 }
const primaryBtnStyle: React.CSSProperties = { padding: '8px 18px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontWeight: 600, fontSize: 14 }
const secondaryBtnStyle: React.CSSProperties = { padding: '8px 18px', background: '#fff', color: '#374151', border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontWeight: 600, fontSize: 14 }
const cancelBtnStyle: React.CSSProperties = { flex: 1, padding: 10, border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontSize: 14, background: '#fff', color: '#374151' }
const deleteBtnStyle: React.CSSProperties = { flex: 1, padding: 10, background: '#fff', color: '#ef4444', border: '1px solid #fca5a5', borderRadius: 8, cursor: 'pointer', fontSize: 14, fontWeight: 600 }
const labelStyle: React.CSSProperties = { display: 'block', fontSize: 13, fontWeight: 500, color: '#374151', marginBottom: 4 }
const inputStyle: React.CSSProperties = { width: '100%', padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' }
