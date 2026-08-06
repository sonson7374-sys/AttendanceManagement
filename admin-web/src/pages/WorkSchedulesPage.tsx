import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getWorkSchedules, createWorkSchedule, updateWorkSchedule, deleteWorkSchedule, getMyWorkSchedule, getWorkScheduleOptions } from '@/api/schedules'
import { getMyWorkScheduleChangeRequests, submitWorkScheduleChangeRequest } from '@/api/workScheduleChangeRequests'
import type { WorkSchedule, WorkScheduleType, WorkScheduleChangeRequest } from '@/types'
import { useAuthStore } from '@/store/authStore'
import { usePermissions } from '@/hooks/usePermissions'
import toast from 'react-hot-toast'

const fmtTime = (t: string) => t?.slice(0, 5) ?? '-'
const fmtMin = (m: number) => `${Math.floor(m / 60)}h ${m % 60}m`
const fmtYearMonth = (ym: string) => `${Number(ym.split('-')[1])}월`

const SCHEDULE_TYPE_OPTIONS: { value: WorkScheduleType; label: string }[] = [
  { value: 'FIXED', label: '고정 근무제' },
  { value: 'FLEXTIME', label: '시차 출퇴근제' },
  { value: 'SELECTIVE', label: '선택 근무제' },
  { value: 'ELASTIC', label: '탄력 근무제' },
  { value: 'SHIFT', label: '교대 근무제' },
  { value: 'REMOTE', label: '재택 근무제' },
]

interface FormData {
  name: string; workStartTime: string; workEndTime: string
  requiredWorkMinutes: string; overtimeThresholdMin: string; defaultSchedule: boolean
  scheduleType: WorkScheduleType; lateThresholdMinutes: string; earlyLeaveThresholdMinutes: string
  breakMinutes: string; nightShiftStart: string; nightShiftEnd: string; holidayWorkThresholdMinutes: string
}
const DEFAULT_FORM: FormData = {
  name: '', workStartTime: '09:00', workEndTime: '18:00',
  requiredWorkMinutes: '480', overtimeThresholdMin: '480', defaultSchedule: false,
  scheduleType: 'FIXED', lateThresholdMinutes: '0', earlyLeaveThresholdMinutes: '0',
  breakMinutes: '60', nightShiftStart: '', nightShiftEnd: '', holidayWorkThresholdMinutes: '0',
}

function ScheduleModal({ schedule, onClose }: { schedule?: WorkSchedule; onClose: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState<FormData>(schedule ? {
    name: schedule.name, workStartTime: schedule.workStartTime.slice(0, 5),
    workEndTime: schedule.workEndTime.slice(0, 5),
    requiredWorkMinutes: String(schedule.requiredWorkMinutes),
    overtimeThresholdMin: String(schedule.overtimeThresholdMin),
    defaultSchedule: schedule.defaultSchedule,
    scheduleType: schedule.scheduleType, lateThresholdMinutes: String(schedule.lateThresholdMinutes),
    earlyLeaveThresholdMinutes: String(schedule.earlyLeaveThresholdMinutes),
    breakMinutes: String(schedule.breakMinutes),
    nightShiftStart: schedule.nightShiftStart?.slice(0, 5) ?? '', nightShiftEnd: schedule.nightShiftEnd?.slice(0, 5) ?? '',
    holidayWorkThresholdMinutes: String(schedule.holidayWorkThresholdMinutes),
  } : DEFAULT_FORM)

  const set = <K extends keyof FormData>(k: K, v: FormData[K]) => setForm(p => ({ ...p, [k]: v }))

  const mutation = useMutation({
    mutationFn: () => {
      const payload = {
        name: form.name, workStartTime: form.workStartTime, workEndTime: form.workEndTime,
        requiredWorkMinutes: Number(form.requiredWorkMinutes),
        overtimeThresholdMin: Number(form.overtimeThresholdMin),
        defaultSchedule: form.defaultSchedule,
        scheduleType: form.scheduleType,
        lateThresholdMinutes: Number(form.lateThresholdMinutes),
        earlyLeaveThresholdMinutes: Number(form.earlyLeaveThresholdMinutes),
        breakMinutes: Number(form.breakMinutes),
        nightShiftStart: form.nightShiftStart || undefined,
        nightShiftEnd: form.nightShiftEnd || undefined,
        holidayWorkThresholdMinutes: Number(form.holidayWorkThresholdMinutes),
      }
      return schedule ? updateWorkSchedule(schedule.id, payload) : createWorkSchedule(payload)
    },
    onSuccess: () => {
      toast.success(schedule ? '수정되었습니다.' : '등록되었습니다.')
      qc.invalidateQueries({ queryKey: ['work-schedules'] }); onClose()
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '오류 발생'),
  })

  return (
    <Overlay>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 460, maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, color: '#1e293b', marginBottom: 24 }}>{schedule ? '근무제 수정' : '근무제 등록'}</h2>
        <form onSubmit={e => { e.preventDefault(); mutation.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Field label="근무제명">
            <input value={form.name} onChange={e => set('name', e.target.value)} required style={inputStyle} placeholder="예: 기본 근무제" />
          </Field>
          <Field label="근무제 유형">
            <select value={form.scheduleType} onChange={e => set('scheduleType', e.target.value as WorkScheduleType)} style={inputStyle}>
              {SCHEDULE_TYPE_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <Field label="근무 시작">
              <input type="time" value={form.workStartTime} onChange={e => set('workStartTime', e.target.value)} required style={inputStyle} />
            </Field>
            <Field label="근무 종료">
              <input type="time" value={form.workEndTime} onChange={e => set('workEndTime', e.target.value)} required style={inputStyle} />
            </Field>
            <Field label="소정 근무시간(분)">
              <input type="number" value={form.requiredWorkMinutes} onChange={e => set('requiredWorkMinutes', e.target.value)} min={60} max={720} required style={inputStyle} />
            </Field>
            <Field label="연장근무 기준(분)">
              <input type="number" value={form.overtimeThresholdMin} onChange={e => set('overtimeThresholdMin', e.target.value)} min={60} max={720} required style={inputStyle} />
            </Field>
            <Field label="지각 기준(분)">
              <input type="number" value={form.lateThresholdMinutes} onChange={e => set('lateThresholdMinutes', e.target.value)} min={0} max={120} required style={inputStyle} />
            </Field>
            <Field label="조퇴 기준(분)">
              <input type="number" value={form.earlyLeaveThresholdMinutes} onChange={e => set('earlyLeaveThresholdMinutes', e.target.value)} min={0} max={120} required style={inputStyle} />
            </Field>
            <Field label="휴게시간(분)">
              <input type="number" value={form.breakMinutes} onChange={e => set('breakMinutes', e.target.value)} min={0} max={480} required style={inputStyle} />
            </Field>
            <Field label="휴일근무 기준(분)">
              <input type="number" value={form.holidayWorkThresholdMinutes} onChange={e => set('holidayWorkThresholdMinutes', e.target.value)} min={0} max={720} required style={inputStyle} />
            </Field>
            <Field label="야간근무 시작">
              <input type="time" value={form.nightShiftStart} onChange={e => set('nightShiftStart', e.target.value)} style={inputStyle} />
            </Field>
            <Field label="야간근무 종료">
              <input type="time" value={form.nightShiftEnd} onChange={e => set('nightShiftEnd', e.target.value)} style={inputStyle} />
            </Field>
          </div>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, color: '#374151', cursor: 'pointer' }}>
            <input type="checkbox" checked={form.defaultSchedule} onChange={e => set('defaultSchedule', e.target.checked)} />
            기본 근무제로 설정
          </label>
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

// ─── 근무제 변경요청 모달(직원용) ───────────────────────────
// 근무제를 직접 새로 정의하지 않고, 기존에 등록된 근무제 목록 중 하나를 선택해 변경을 신청한다.
// 적용 예정월은 다음 달부터 지정 가능하며(당월 변경 불가), 신청 즉시 반영되지 않고 관리자 승인함에서 승인되면 실제로 반영된다.
function WorkScheduleChangeRequestModal({ currentSchedule, onClose }: { currentSchedule: WorkSchedule; onClose: () => void }) {
  const qc = useQueryClient()
  const nextMonth = (() => {
    const d = new Date()
    d.setMonth(d.getMonth() + 1)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  })()
  const [targetWorkScheduleId, setTargetWorkScheduleId] = useState<number | ''>('')
  const [effectiveMonth, setEffectiveMonth] = useState(nextMonth)
  const [reason, setReason] = useState('')

  const { data: options = [] } = useQuery({
    queryKey: ['work-schedule-options'],
    queryFn: () => getWorkScheduleOptions().then(r => r.data.data),
  })
  const selectableOptions = options.filter(o => o.id !== currentSchedule.id)

  const mutation = useMutation({
    mutationFn: () => {
      if (!targetWorkScheduleId) throw new Error('변경할 근무제를 선택해주세요.')
      if (effectiveMonth < nextMonth) throw new Error('적용 예정월은 다음 달 이후여야 합니다.')
      if (!reason.trim()) throw new Error('사유를 입력해주세요.')
      return submitWorkScheduleChangeRequest({
        currentWorkScheduleId: currentSchedule.id,
        targetWorkScheduleId: targetWorkScheduleId as number,
        effectiveMonth, reason,
      })
    },
    onSuccess: () => {
      toast.success('근무제 변경요청이 접수되었습니다. 관리자 승인 후 반영됩니다.')
      qc.invalidateQueries({ queryKey: ['my-work-schedule-change-requests'] })
      onClose()
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? e?.message ?? '신청 실패'),
  })

  return (
    <Overlay>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 460, maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 24, color: '#1e293b' }}>근무제</h2>
        <p style={{ fontSize: 14, color: '#374151', marginBottom: 16 }}>
          현재 적용 근무제: <b>{currentSchedule.name}({fmtTime(currentSchedule.workStartTime)})</b>
        </p>
        <form onSubmit={e => { e.preventDefault(); mutation.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ display: 'flex', gap: 8 }}>
            <select
              value={targetWorkScheduleId}
              onChange={e => setTargetWorkScheduleId(e.target.value ? Number(e.target.value) : '')}
              style={{ ...inputStyle, flex: 1 }}
            >
              <option value="">근무제 선택...</option>
              {selectableOptions.map(o => (
                <option key={o.id} value={o.id}>{o.name}({fmtTime(o.workStartTime)})</option>
              ))}
            </select>
          </div>
          <Field label="적용 예정월">
            <input type="month" value={effectiveMonth} min={nextMonth} onChange={e => setEffectiveMonth(e.target.value)} required style={inputStyle} />
          </Field>
          <Field label="사유">
            <textarea value={reason} onChange={e => setReason(e.target.value)} required rows={3} style={{ ...inputStyle, resize: 'vertical' }} />
          </Field>
          <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
            <button type="button" onClick={onClose} style={cancelBtnStyle}>취소</button>
            <button type="submit" disabled={mutation.isPending} style={primaryBtnStyle}>{mutation.isPending ? '신청 중...' : '변경'}</button>
          </div>
        </form>
      </div>
    </Overlay>
  )
}

export default function WorkSchedulesPage() {
  const qc = useQueryClient()
  const [modal, setModal] = useState<WorkSchedule | null | 'new'>(null)
  const [changeRequestTarget, setChangeRequestTarget] = useState<WorkSchedule | null>(null)
  const role = useAuthStore(s => s.role)
  // 일반 직원(role=EMPLOYEE)은 전체 근무제 목록 조회 권한이 없다(서버 GET /work-schedules가 MANAGER 이상만 허용) —
  // 본인에게 적용된 근무제만 보여주고, 등록·수정·삭제는 아예 노출하지 않는다. 대신 변경요청만 가능하다.
  const isPlainEmployee = role === 'EMPLOYEE'

  const { data: schedules = [], isLoading } = useQuery({
    queryKey: isPlainEmployee ? ['work-schedules', 'assigned'] : ['work-schedules'],
    queryFn: () => isPlainEmployee
      ? getMyWorkSchedule().then(r => [r.data.data])
      : getWorkSchedules().then(r => r.data.data),
  })

  const { data: myChangeRequests = [] } = useQuery({
    queryKey: ['my-work-schedule-change-requests'],
    queryFn: () => getMyWorkScheduleChangeRequests().then(r => r.data.data),
    enabled: isPlainEmployee,
  })
  const pendingChangeRequestByScheduleId = new Map(
    myChangeRequests.filter(r => r.status === 'PENDING' && r.currentWorkScheduleId != null).map(r => [r.currentWorkScheduleId as number, r]),
  )
  // 검토중이 아니어도(승인/반려) 가장 최근 변경요청 상태를 근무제 카드에 표시한다.
  // 승인된 경우 적용예정월 전까지는 기존 근무제 카드가 계속 보이므로, 그 카드에 "몇 월부터 자동 전환" 안내를 함께 보여준다.
  const latestChangeRequestByScheduleId = new Map<number, WorkScheduleChangeRequest>()
  for (const r of myChangeRequests) {
    if (r.currentWorkScheduleId == null) continue
    const existing = latestChangeRequestByScheduleId.get(r.currentWorkScheduleId)
    if (!existing || new Date(r.createdAt).getTime() > new Date(existing.createdAt).getTime()) {
      latestChangeRequestByScheduleId.set(r.currentWorkScheduleId, r)
    }
  }

  const del = useMutation({
    mutationFn: (id: number) => deleteWorkSchedule(id),
    onSuccess: () => { toast.success('삭제되었습니다.'); qc.invalidateQueries({ queryKey: ['work-schedules'] }) },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '삭제 실패'),
  })
  const { isActionEnabled } = usePermissions()
  const canCreate = !isPlainEmployee && isActionEnabled('work-schedules', 'CREATE')
  const canEdit = !isPlainEmployee && isActionEnabled('work-schedules', 'EDIT')

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, color: '#1e293b' }}>근무제 관리</h1>
        {canCreate && <button onClick={() => setModal('new')} style={primaryBtnStyle}>+ 근무제 등록</button>}
      </div>

      {isLoading ? <p style={{ color: '#64748b' }}>로딩 중...</p> : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 16 }}>
          {schedules.map((s: WorkSchedule) => {
            const pending = pendingChangeRequestByScheduleId.get(s.id)
            const latest = latestChangeRequestByScheduleId.get(s.id)
            const isApproved = latest?.status === 'APPROVED'
            const isRejected = latest?.status === 'REJECTED'
            return (
            <div key={s.id} style={{ background: '#fff', borderRadius: 12, padding: 20, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', borderTop: s.defaultSchedule ? '4px solid #2563eb' : '4px solid #e2e8f0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
                <div>
                  <h3 style={{ fontSize: 15, fontWeight: 600, color: '#1e293b' }}>{s.name}</h3>
                  <span style={{ fontSize: 11, color: '#64748b' }}>
                    {SCHEDULE_TYPE_OPTIONS.find(o => o.value === s.scheduleType)?.label ?? s.scheduleType}
                  </span>
                  {s.defaultSchedule && <span style={{ fontSize: 11, color: '#2563eb', fontWeight: 600, marginLeft: 8 }}>기본</span>}
                </div>
                {pending && (
                  <span style={{ padding: '2px 10px', borderRadius: 20, fontSize: 11, fontWeight: 600, background: '#fff7ed', color: '#f97316' }}>
                    변경요청 검토중
                  </span>
                )}
                {isApproved && (
                  <span style={{ padding: '2px 10px', borderRadius: 20, fontSize: 11, fontWeight: 600, background: '#eff6ff', color: '#2563eb' }}>
                    변경요청 승인됨
                  </span>
                )}
                {isRejected && (
                  <span style={{ padding: '2px 10px', borderRadius: 20, fontSize: 11, fontWeight: 600, background: '#fef2f2', color: '#ef4444' }}>
                    변경요청 반려됨
                  </span>
                )}
              </div>

              {pending && (
                <div style={{ background: '#fff7ed', border: '1px solid #fdba74', borderRadius: 8, padding: '10px 12px', marginBottom: 12, fontSize: 13, color: '#c2410c' }}>
                  <p style={{ margin: 0 }}><b>변경할 근무제:</b> {pending.targetWorkScheduleName ?? `#${pending.targetWorkScheduleId}`}</p>
                  <p style={{ margin: '4px 0 0' }}>적용 예정월: {pending.effectiveMonth} (관리자 승인 대기중)</p>
                </div>
              )}

              {isApproved && (
                <div style={{ background: '#eff6ff', border: '1px solid #bfdbfe', borderRadius: 8, padding: '10px 12px', marginBottom: 12, fontSize: 13, color: '#1d4ed8' }}>
                  {fmtYearMonth(latest!.effectiveMonth)}부터 <b>{latest!.targetWorkScheduleName ?? `#${latest!.targetWorkScheduleId}`}</b>(으)로 근무제가 변경됩니다.
                </div>
              )}

              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {[
                  ['근무 시간', `${fmtTime(s.workStartTime)} ~ ${fmtTime(s.workEndTime)}`],
                  ['소정 근무', fmtMin(s.requiredWorkMinutes)],
                  ['연장 기준', fmtMin(s.overtimeThresholdMin)],
                  ['지각/조퇴 기준', `${s.lateThresholdMinutes}분 / ${s.earlyLeaveThresholdMinutes}분`],
                  ['휴게시간', `${s.breakMinutes}분`],
                ].map(([label, val]) => (
                  <div key={label} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
                    <span style={{ color: '#64748b' }}>{label}</span>
                    <span style={{ fontWeight: 500, color: '#374151' }}>{val}</span>
                  </div>
                ))}
              </div>
              <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
                {canEdit && <button onClick={() => setModal(s)} style={editBtnStyle}>수정</button>}
                {!isPlainEmployee && (
                  <button onClick={() => del.mutate(s.id)} disabled={s.defaultSchedule} style={{ ...deleteBtnStyle, opacity: s.defaultSchedule ? 0.4 : 1 }}>삭제</button>
                )}
                {isPlainEmployee && (
                  <button
                    onClick={() => setChangeRequestTarget(s)}
                    disabled={!!pending}
                    style={{
                      flex: 1, padding: '6px 0', fontSize: 13, fontWeight: 600, borderRadius: 6,
                      border: '1px solid #fdba74', background: pending ? '#f8fafc' : '#fff7ed', color: pending ? '#94a3b8' : '#f97316',
                      cursor: pending ? 'not-allowed' : 'pointer',
                    }}
                  >
                    {pending ? '검토중' : '변경요청'}
                  </button>
                )}
              </div>
            </div>
            )
          })}
          {schedules.length === 0 && (
            <div style={{ gridColumn: '1/-1', padding: 48, textAlign: 'center', color: '#64748b', background: '#fff', borderRadius: 12 }}>등록된 근무제가 없습니다.</div>
          )}
        </div>
      )}

      {modal === 'new' && <ScheduleModal onClose={() => setModal(null)} />}
      {modal && modal !== 'new' && <ScheduleModal schedule={modal as WorkSchedule} onClose={() => setModal(null)} />}
      {changeRequestTarget && <WorkScheduleChangeRequestModal currentSchedule={changeRequestTarget} onClose={() => setChangeRequestTarget(null)} />}
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
const primaryBtnStyle: React.CSSProperties = { padding: '8px 18px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontWeight: 600, fontSize: 14 }
const cancelBtnStyle: React.CSSProperties = { flex: 1, padding: 10, border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontSize: 14, background: '#fff', color: '#374151' }
const editBtnStyle: React.CSSProperties = { flex: 1, padding: '6px 0', fontSize: 13, color: '#2563eb', border: '1px solid #bfdbfe', borderRadius: 6, background: '#fff', cursor: 'pointer' }
const deleteBtnStyle: React.CSSProperties = { flex: 1, padding: '6px 0', fontSize: 13, color: '#ef4444', border: '1px solid #fca5a5', borderRadius: 6, background: '#fff', cursor: 'pointer' }
