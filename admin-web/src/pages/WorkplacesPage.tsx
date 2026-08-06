import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getWorkplaces, createWorkplace, updateWorkplace, deactivateWorkplace, activateWorkplace, getAssignedUsers, assignUserToWorkplace, removeUserFromWorkplace, bulkAssignUsersToWorkplace } from '@/api/workplaces'
import { getMyAssignedWorkplaces } from '@/api/myAttendance'
import { getMyWorkplaceChangeRequests, submitWorkplaceChangeRequest } from '@/api/workplaceChangeRequests'
import { getUsers } from '@/api/users'
import type { Workplace, User, WorkplaceType, WorkplaceChangeRequest } from '@/types'
import KakaoMap, { geocodeAddress } from '@/components/KakaoMap'
import { useAuthStore } from '@/store/authStore'
import { usePermissions } from '@/hooks/usePermissions'
import toast from 'react-hot-toast'

const COMPANY_ID = 1

const WORKPLACE_TYPE_OPTIONS: { value: WorkplaceType; label: string }[] = [
  { value: 'OFFICE', label: '일반 사무실' },
  { value: 'LARGE_SITE', label: '대형 사업장' },
  { value: 'CONSTRUCTION_SITE', label: '건설 현장' },
  { value: 'INDOOR', label: '지하·실내' },
  { value: 'OTHER', label: '기타' },
]

// ─── 근무지 생성 모달 ───────────────────────────────────────
interface CreateFormData {
  name: string; address: string; detailAddress: string; type: WorkplaceType
  latitude: string; longitude: string; radiusMeters: string; maxAccuracyMeters: string
  checkInAllowed: boolean; checkOutAllowed: boolean; validFrom: string; validTo: string
}

function CreateWorkplaceModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<CreateFormData>({
    name: '', address: '', detailAddress: '', type: 'OFFICE',
    latitude: '37.5665', longitude: '126.9780', radiusMeters: '100', maxAccuracyMeters: '',
    checkInAllowed: true, checkOutAllowed: true, validFrom: '', validTo: '',
  })
  const [searching, setSearching] = useState(false)
  const set = <K extends keyof CreateFormData>(k: K, v: CreateFormData[K]) => setForm(p => ({ ...p, [k]: v }))

  const searchAddress = async () => {
    if (!form.address.trim()) { toast.error('주소를 입력해주세요.'); return }
    setSearching(true)
    try {
      const result = await geocodeAddress(form.address)
      setForm(p => ({ ...p, latitude: String(result.latitude), longitude: String(result.longitude) }))
      toast.success(`좌표를 찾았습니다: ${result.addressName}`)
    } catch (e: any) {
      toast.error(e?.message ?? '주소 검색 실패')
    } finally {
      setSearching(false)
    }
  }

  const mutation = useMutation({
    mutationFn: () => createWorkplace({
      companyId: COMPANY_ID, name: form.name, address: form.address, detailAddress: form.detailAddress || undefined,
      type: form.type,
      latitude: parseFloat(form.latitude), longitude: parseFloat(form.longitude),
      radiusMeters: parseInt(form.radiusMeters),
      maxAccuracyMeters: form.maxAccuracyMeters ? parseInt(form.maxAccuracyMeters) : undefined,
      checkInAllowed: form.checkInAllowed, checkOutAllowed: form.checkOutAllowed,
      validFrom: form.validFrom || undefined, validTo: form.validTo || undefined,
    }),
    onSuccess: () => { toast.success('근무지가 등록되었습니다.'); queryClient.invalidateQueries({ queryKey: ['workplaces'] }); onClose() },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '등록 실패'),
  })

  const lat = parseFloat(form.latitude) || 37.5665
  const lng = parseFloat(form.longitude) || 126.978
  const radius = parseInt(form.radiusMeters) || 100

  return (
    <Overlay>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 500, maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 24, color: '#1e293b' }}>근무지 등록</h2>
        <form onSubmit={e => { e.preventDefault(); mutation.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Field label="근무지명">
            <input value={form.name} onChange={e => set('name', e.target.value)} required style={inputStyle} placeholder="예: 서울 본사" />
          </Field>
          <Field label="근무지 유형">
            <select value={form.type} onChange={e => set('type', e.target.value as WorkplaceType)} style={inputStyle}>
              {WORKPLACE_TYPE_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </Field>
          <Field label="주소">
            <div style={{ display: 'flex', gap: 8 }}>
              <input
                value={form.address}
                onChange={e => set('address', e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); searchAddress() } }}
                style={{ ...inputStyle, flex: 1 }}
                placeholder="예: 서울시 중구 을지로 1가"
              />
              <button
                type="button"
                onClick={searchAddress}
                disabled={searching}
                style={{ padding: '0 16px', background: '#f1f5f9', color: '#374151', border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 600, whiteSpace: 'nowrap' }}
              >{searching ? '검색 중...' : '주소 검색'}</button>
            </div>
          </Field>
          <Field label="상세 주소">
            <input value={form.detailAddress} onChange={e => set('detailAddress', e.target.value)} style={inputStyle} placeholder="예: 3층 사무실" />
          </Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10 }}>
            <Field label="위도">
              <input value={form.latitude} onChange={e => set('latitude', e.target.value)} required style={inputStyle} />
            </Field>
            <Field label="경도">
              <input value={form.longitude} onChange={e => set('longitude', e.target.value)} required style={inputStyle} />
            </Field>
            <Field label="반경(m)">
              <input type="number" value={form.radiusMeters} onChange={e => set('radiusMeters', e.target.value)} min={10} max={5000} required style={inputStyle} />
            </Field>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10 }}>
            <Field label="허용 정확도(m)">
              <input type="number" value={form.maxAccuracyMeters} onChange={e => set('maxAccuracyMeters', e.target.value)} min={1} style={inputStyle} placeholder="기본값 사용" />
            </Field>
            <Field label="사용 시작일">
              <input type="date" value={form.validFrom} onChange={e => set('validFrom', e.target.value)} style={inputStyle} />
            </Field>
            <Field label="사용 종료일">
              <input type="date" value={form.validTo} onChange={e => set('validTo', e.target.value)} style={inputStyle} />
            </Field>
          </div>
          <div style={{ display: 'flex', gap: 20 }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: '#374151' }}>
              <input type="checkbox" checked={form.checkInAllowed} onChange={e => set('checkInAllowed', e.target.checked)} /> 출근 허용
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: '#374151' }}>
              <input type="checkbox" checked={form.checkOutAllowed} onChange={e => set('checkOutAllowed', e.target.checked)} /> 퇴근 허용
            </label>
          </div>

          {/* 지도 미리보기 — 클릭 또는 마커 드래그로 좌표 지정 */}
          <div>
            <p style={{ fontSize: 12, color: '#64748b', marginBottom: 6 }}>위치 미리보기 (지도를 클릭하거나 마커를 드래그해 좌표를 지정할 수 있습니다)</p>
            <KakaoMap
              latitude={lat} longitude={lng} radiusMeters={radius} height={180}
              editable
              onPositionChange={(newLat, newLng) => setForm(p => ({ ...p, latitude: String(newLat), longitude: String(newLng) }))}
            />
          </div>

          <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
            <button type="button" onClick={onClose} style={cancelBtnStyle}>취소</button>
            <button type="submit" disabled={mutation.isPending} style={primaryBtnStyle}>
              {mutation.isPending ? '등록 중...' : '등록'}
            </button>
          </div>
        </form>
      </div>
    </Overlay>
  )
}

// ─── 직원 배정 모달 ─────────────────────────────────────────
function AssignUserModal({ workplace, onClose }: { workplace: Workplace; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [selectedUserId, setSelectedUserId] = useState<number | ''>('')
  const [bulkSelectedIds, setBulkSelectedIds] = useState<Set<number>>(new Set())
  const [bulkMode, setBulkMode] = useState(false)

  const { data: assignedUsers = [] } = useQuery({
    queryKey: ['workplace-users', workplace.id],
    queryFn: () => getAssignedUsers(workplace.id).then(r => r.data.data),
  })

  const { data: allUsersPage } = useQuery({
    queryKey: ['users', 0],
    queryFn: () => getUsers(0, 100).then(r => r.data.data),
  })
  const allUsers = allUsersPage?.content ?? []
  const assignedIds = new Set(assignedUsers.map((u: User) => u.id))
  const unassigned = allUsers.filter((u: User) => !assignedIds.has(u.id) && u.status === 'ACTIVE')

  const assign = useMutation({
    mutationFn: (userId: number) => assignUserToWorkplace(workplace.id, userId),
    onSuccess: () => {
      toast.success('배정되었습니다.')
      queryClient.invalidateQueries({ queryKey: ['workplace-users', workplace.id] })
      setSelectedUserId('')
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '배정 실패'),
  })

  const bulkAssign = useMutation({
    mutationFn: () => bulkAssignUsersToWorkplace(workplace.id, Array.from(bulkSelectedIds)),
    onSuccess: () => {
      toast.success(`${bulkSelectedIds.size}명이 일괄 배정되었습니다.`)
      queryClient.invalidateQueries({ queryKey: ['workplace-users', workplace.id] })
      setBulkSelectedIds(new Set())
      setBulkMode(false)
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '일괄 배정 실패'),
  })

  const remove = useMutation({
    mutationFn: (userId: number) => removeUserFromWorkplace(workplace.id, userId),
    onSuccess: () => {
      toast.success('배정이 해제되었습니다.')
      queryClient.invalidateQueries({ queryKey: ['workplace-users', workplace.id] })
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '해제 실패'),
  })

  const toggleBulk = (userId: number) => {
    setBulkSelectedIds(prev => {
      const next = new Set(prev)
      if (next.has(userId)) next.delete(userId); else next.add(userId)
      return next
    })
  }

  return (
    <Overlay>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 480, maxHeight: '85vh', overflowY: 'auto', boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 4, color: '#1e293b' }}>직원 배정</h2>
            <p style={{ fontSize: 13, color: '#64748b', marginBottom: 24 }}>{workplace.name}</p>
          </div>
          <button type="button" onClick={() => setBulkMode(m => !m)} style={{ fontSize: 12, color: '#2563eb', background: 'none', border: 'none', cursor: 'pointer' }}>
            {bulkMode ? '단건 배정으로 전환' : '일괄 배정 모드'}
          </button>
        </div>

        {bulkMode ? (
          <div style={{ marginBottom: 20 }}>
            <div style={{ maxHeight: 220, overflowY: 'auto', border: '1px solid #e2e8f0', borderRadius: 8, padding: 8 }}>
              {unassigned.length === 0 ? (
                <p style={{ fontSize: 13, color: '#94a3b8', padding: 8 }}>배정 가능한 직원이 없습니다.</p>
              ) : unassigned.map((u: User) => (
                <label key={u.id} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 4px', fontSize: 13, color: '#374151', cursor: 'pointer' }}>
                  <input type="checkbox" checked={bulkSelectedIds.has(u.id)} onChange={() => toggleBulk(u.id)} />
                  {u.name} ({u.employeeNumber})
                </label>
              ))}
            </div>
            <button
              onClick={() => bulkAssign.mutate()}
              disabled={bulkSelectedIds.size === 0 || bulkAssign.isPending}
              style={{ marginTop: 10, width: '100%', padding: '8px 16px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 14, fontWeight: 600, opacity: bulkSelectedIds.size === 0 ? 0.5 : 1 }}
            >
              선택한 {bulkSelectedIds.size}명 일괄 배정
            </button>
          </div>
        ) : (
          <div style={{ display: 'flex', gap: 8, marginBottom: 20 }}>
            <select
              value={selectedUserId}
              onChange={e => setSelectedUserId(Number(e.target.value) || '')}
              style={{ flex: 1, padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14 }}
            >
              <option value="">직원 선택...</option>
              {unassigned.map((u: User) => (
                <option key={u.id} value={u.id}>{u.name} ({u.employeeNumber})</option>
              ))}
            </select>
            <button
              onClick={() => selectedUserId && assign.mutate(selectedUserId as number)}
              disabled={!selectedUserId || assign.isPending}
              style={{ padding: '8px 16px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 14, fontWeight: 600, opacity: !selectedUserId ? 0.5 : 1 }}
            >
              배정
            </button>
          </div>
        )}

        {/* 현재 배정 직원 목록 */}
        <div>
          <p style={{ fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 8 }}>
            현재 배정 직원 ({assignedUsers.length}명)
          </p>
          {assignedUsers.length === 0 ? (
            <p style={{ fontSize: 13, color: '#94a3b8', padding: '16px 0' }}>배정된 직원이 없습니다.</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {assignedUsers.map((u: User) => (
                <div key={u.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 12px', background: '#f8fafc', borderRadius: 8 }}>
                  <div>
                    <span style={{ fontSize: 14, fontWeight: 500, color: '#1e293b' }}>{u.name}</span>
                    <span style={{ fontSize: 12, color: '#94a3b8', marginLeft: 8 }}>{u.employeeNumber}</span>
                  </div>
                  <button
                    onClick={() => remove.mutate(u.id)}
                    disabled={remove.isPending}
                    style={{ padding: '4px 10px', fontSize: 12, color: '#ef4444', border: '1px solid #fca5a5', borderRadius: 6, background: '#fff', cursor: 'pointer' }}
                  >
                    해제
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>

        <button onClick={onClose} style={{ ...cancelBtnStyle, width: '100%', marginTop: 20 }}>닫기</button>
      </div>
    </Overlay>
  )
}

// ─── 근무지 카드 ────────────────────────────────────────────
function formatMonthDay(dateStr: string) {
  const d = new Date(`${dateStr}T00:00:00`)
  return `${d.getMonth() + 1}월 ${d.getDate()}일`
}

function WorkplaceCard({ workplace, onAssign, onEdit, onDelete, onRestore, canManage, canEdit, canRequestChange, onRequestChange, pendingChangeRequest, latestChangeRequest }: {
  workplace: Workplace; onAssign: () => void; onEdit: () => void; onDelete: () => void; onRestore: () => void
  canManage: boolean; canEdit: boolean
  canRequestChange?: boolean; onRequestChange?: () => void; pendingChangeRequest?: WorkplaceChangeRequest; latestChangeRequest?: WorkplaceChangeRequest
}) {
  const isPending = latestChangeRequest?.status === 'PENDING'
  const isApproved = latestChangeRequest?.status === 'APPROVED'
  const isRejected = latestChangeRequest?.status === 'REJECTED'
  return (
    <div style={{ background: '#fff', borderRadius: 12, padding: 20, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', borderTop: workplace.active ? '4px solid #10b981' : '4px solid #e2e8f0' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
        <div>
          <h3 style={{ fontSize: 15, fontWeight: 600, color: '#1e293b' }}>{workplace.name}</h3>
          {workplace.address && <p style={{ fontSize: 12, color: '#64748b', marginTop: 2 }}>{workplace.address}{workplace.detailAddress ? ` ${workplace.detailAddress}` : ''}</p>}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4, alignItems: 'flex-end' }}>
          <span style={{ padding: '2px 10px', borderRadius: 20, fontSize: 11, fontWeight: 600, background: workplace.active ? '#dcfce7' : '#f1f5f9', color: workplace.active ? '#16a34a' : '#94a3b8' }}>
            {workplace.active ? '활성' : '비활성'}
          </span>
          {isPending && (
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
      </div>

      {isApproved && (
        <div style={{ background: '#eff6ff', border: '1px solid #bfdbfe', borderRadius: 8, padding: '10px 12px', marginBottom: 12, fontSize: 13, color: '#1d4ed8' }}>
          {formatMonthDay(latestChangeRequest!.effectiveDate)}부터 <b>{latestChangeRequest!.name}</b>(으)로 자동 전환됩니다.
        </div>
      )}

      <div style={{ display: 'flex', gap: 6, marginBottom: 10, flexWrap: 'wrap' }}>
        <span style={{ padding: '2px 8px', borderRadius: 6, fontSize: 11, background: '#eff6ff', color: '#2563eb' }}>
          {WORKPLACE_TYPE_OPTIONS.find(o => o.value === workplace.type)?.label ?? workplace.type}
        </span>
        {!workplace.checkInAllowed && <span style={{ padding: '2px 8px', borderRadius: 6, fontSize: 11, background: '#fef2f2', color: '#ef4444' }}>출근 불가</span>}
        {!workplace.checkOutAllowed && <span style={{ padding: '2px 8px', borderRadius: 6, fontSize: 11, background: '#fef2f2', color: '#ef4444' }}>퇴근 불가</span>}
      </div>

      {/* 지도 */}
      <KakaoMap latitude={workplace.latitude} longitude={workplace.longitude} radiusMeters={workplace.radiusMeters} height={160} />

      {/* 좌표/반경 정보 */}
      <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
        {[['위도', workplace.latitude.toFixed(4)], ['경도', workplace.longitude.toFixed(4)], ['반경', `${workplace.radiusMeters}m`]].map(([label, val]) => (
          <div key={label} style={{ flex: 1, background: '#f8fafc', borderRadius: 6, padding: '6px 10px' }}>
            <p style={{ fontSize: 10, color: '#94a3b8' }}>{label}</p>
            <p style={{ fontSize: 13, fontWeight: 500, color: '#374151' }}>{val}</p>
          </div>
        ))}
      </div>

      <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
        {workplace.active ? (
          <>
            {canEdit && (
              <button onClick={onEdit} style={{ flex: 1, padding: '8px 0', background: '#fff', color: '#374151', border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 600 }}>
                수정
              </button>
            )}
            {canEdit && (
              <button onClick={onAssign} style={{ flex: 2, padding: '8px 0', background: '#eff6ff', color: '#2563eb', border: '1px solid #bfdbfe', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 600 }}>
                직원 배정 관리
              </button>
            )}
            {canManage && (
              <button onClick={onDelete} style={{ flex: 1, padding: '8px 0', background: '#fff', color: '#ef4444', border: '1px solid #fca5a5', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 600 }}>
                삭제
              </button>
            )}
            {canRequestChange && (
              <button
                onClick={onRequestChange}
                disabled={!!pendingChangeRequest}
                style={{
                  flex: 1, padding: '8px 0', border: '1px solid #fdba74', borderRadius: 8, fontSize: 13, fontWeight: 600,
                  background: pendingChangeRequest ? '#f8fafc' : '#fff7ed', color: pendingChangeRequest ? '#94a3b8' : '#f97316',
                  cursor: pendingChangeRequest ? 'not-allowed' : 'pointer',
                }}
              >
                {pendingChangeRequest ? '검토중' : '변경요청'}
              </button>
            )}
          </>
        ) : (
          canManage && (
            <button onClick={onRestore} style={{ flex: 1, padding: '8px 0', background: '#f0fdf4', color: '#16a34a', border: '1px solid #bbf7d0', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 600 }}>
              복구
            </button>
          )
        )}
      </div>
    </div>
  )
}

// ─── 근무지 편집 모달 ───────────────────────────────────────
function EditWorkplaceModal({ workplace, onClose }: { workplace: Workplace; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState({
    name: workplace.name, address: workplace.address ?? '', detailAddress: workplace.detailAddress ?? '',
    type: workplace.type, latitude: String(workplace.latitude), longitude: String(workplace.longitude),
    radiusMeters: String(workplace.radiusMeters), maxAccuracyMeters: workplace.maxAccuracyMeters ? String(workplace.maxAccuracyMeters) : '',
    checkInAllowed: workplace.checkInAllowed, checkOutAllowed: workplace.checkOutAllowed,
    validFrom: workplace.validFrom ?? '', validTo: workplace.validTo ?? '',
  })
  const [searching, setSearching] = useState(false)
  const set = <K extends keyof typeof form>(k: K, v: (typeof form)[K]) => setForm(p => ({ ...p, [k]: v }))
  const lat = parseFloat(form.latitude) || 37.5665
  const lng = parseFloat(form.longitude) || 126.978
  const radius = parseInt(form.radiusMeters) || 100

  const searchAddress = async () => {
    if (!form.address.trim()) { toast.error('주소를 입력해주세요.'); return }
    setSearching(true)
    try {
      const result = await geocodeAddress(form.address)
      setForm(p => ({ ...p, latitude: String(result.latitude), longitude: String(result.longitude) }))
      toast.success(`좌표를 찾았습니다: ${result.addressName}`)
    } catch (e: any) {
      toast.error(e?.message ?? '주소 검색 실패')
    } finally {
      setSearching(false)
    }
  }

  const mutation = useMutation({
    mutationFn: () => updateWorkplace(workplace.id, {
      companyId: workplace.companyId, name: form.name, address: form.address, detailAddress: form.detailAddress || undefined,
      type: form.type, latitude: lat, longitude: lng, radiusMeters: radius,
      maxAccuracyMeters: form.maxAccuracyMeters ? parseInt(form.maxAccuracyMeters) : undefined,
      checkInAllowed: form.checkInAllowed, checkOutAllowed: form.checkOutAllowed,
      validFrom: form.validFrom || undefined, validTo: form.validTo || undefined,
    }),
    onSuccess: () => { toast.success('근무지가 수정되었습니다.'); queryClient.invalidateQueries({ queryKey: ['workplaces'] }); onClose() },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '수정 실패'),
  })

  return (
    <Overlay>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 500, maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 24, color: '#1e293b' }}>근무지 수정</h2>
        <form onSubmit={e => { e.preventDefault(); mutation.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Field label="근무지명"><input value={form.name} onChange={e => set('name', e.target.value)} required style={inputStyle} /></Field>
          <Field label="근무지 유형">
            <select value={form.type} onChange={e => set('type', e.target.value as WorkplaceType)} style={inputStyle}>
              {WORKPLACE_TYPE_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </Field>
          <Field label="주소">
            <div style={{ display: 'flex', gap: 8 }}>
              <input
                value={form.address}
                onChange={e => set('address', e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); searchAddress() } }}
                style={{ ...inputStyle, flex: 1 }}
              />
              <button
                type="button"
                onClick={searchAddress}
                disabled={searching}
                style={{ padding: '0 16px', background: '#f1f5f9', color: '#374151', border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 600, whiteSpace: 'nowrap' }}
              >{searching ? '검색 중...' : '주소 검색'}</button>
            </div>
          </Field>
          <Field label="상세 주소">
            <input value={form.detailAddress} onChange={e => set('detailAddress', e.target.value)} style={inputStyle} />
          </Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10 }}>
            <Field label="위도"><input value={form.latitude} onChange={e => set('latitude', e.target.value)} required style={inputStyle} /></Field>
            <Field label="경도"><input value={form.longitude} onChange={e => set('longitude', e.target.value)} required style={inputStyle} /></Field>
            <Field label="반경(m)"><input type="number" value={form.radiusMeters} onChange={e => set('radiusMeters', e.target.value)} min={10} max={5000} required style={inputStyle} /></Field>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10 }}>
            <Field label="허용 정확도(m)">
              <input type="number" value={form.maxAccuracyMeters} onChange={e => set('maxAccuracyMeters', e.target.value)} min={1} style={inputStyle} placeholder="기본값 사용" />
            </Field>
            <Field label="사용 시작일">
              <input type="date" value={form.validFrom} onChange={e => set('validFrom', e.target.value)} style={inputStyle} />
            </Field>
            <Field label="사용 종료일">
              <input type="date" value={form.validTo} onChange={e => set('validTo', e.target.value)} style={inputStyle} />
            </Field>
          </div>
          <div style={{ display: 'flex', gap: 20 }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: '#374151' }}>
              <input type="checkbox" checked={form.checkInAllowed} onChange={e => set('checkInAllowed', e.target.checked)} /> 출근 허용
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: '#374151' }}>
              <input type="checkbox" checked={form.checkOutAllowed} onChange={e => set('checkOutAllowed', e.target.checked)} /> 퇴근 허용
            </label>
          </div>
          <p style={{ fontSize: 12, color: '#64748b', margin: '2px 0 -6px' }}>지도를 클릭하거나 마커를 드래그해 좌표를 지정할 수 있습니다</p>
          <KakaoMap
            latitude={lat} longitude={lng} radiusMeters={radius} height={160}
            editable
            onPositionChange={(newLat, newLng) => setForm(p => ({ ...p, latitude: String(newLat), longitude: String(newLng) }))}
          />
          <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
            <button type="button" onClick={onClose} style={cancelBtnStyle}>취소</button>
            <button type="submit" disabled={mutation.isPending} style={primaryBtnStyle}>{mutation.isPending ? '수정 중...' : '수정'}</button>
          </div>
        </form>
      </div>
    </Overlay>
  )
}

// ─── 근무지 변경요청 모달(직원용) ───────────────────────────
// 근무지 등록 모달과 동일한 필드 구성(주소 검색·좌표·반경 등)에 적용 예정일·사유를 더해
// 새 근무지를 직접 등록하는 대신 신청만 하고, 관리자 승인함에서 승인되면 실제로 반영된다.
function WorkplaceChangeRequestModal({ currentWorkplace, onClose }: { currentWorkplace: Workplace; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState({
    name: currentWorkplace.name, address: currentWorkplace.address ?? '', detailAddress: currentWorkplace.detailAddress ?? '',
    latitude: String(currentWorkplace.latitude), longitude: String(currentWorkplace.longitude),
    checkInAllowed: currentWorkplace.checkInAllowed, checkOutAllowed: currentWorkplace.checkOutAllowed,
    effectiveDate: '', reason: '',
  })
  const [searching, setSearching] = useState(false)
  const set = <K extends keyof typeof form>(k: K, v: (typeof form)[K]) => setForm(p => ({ ...p, [k]: v }))
  const lat = parseFloat(form.latitude) || 37.5665
  const lng = parseFloat(form.longitude) || 126.978
  // 근무지 유형·반경·허용 정확도는 시스템 관리자만 지정 가능한 값이므로 신청 화면에서는 받지 않고
  // 기존 근무지에 설정된 값을 그대로 이어받는다(관리자가 승인 시 검토·조정 가능).
  const radius = currentWorkplace.radiusMeters

  const searchAddress = async () => {
    if (!form.address.trim()) { toast.error('주소를 입력해주세요.'); return }
    setSearching(true)
    try {
      const result = await geocodeAddress(form.address)
      setForm(p => ({ ...p, latitude: String(result.latitude), longitude: String(result.longitude) }))
      toast.success(`좌표를 찾았습니다: ${result.addressName}`)
    } catch (e: any) {
      toast.error(e?.message ?? '주소 검색 실패')
    } finally {
      setSearching(false)
    }
  }

  const mutation = useMutation({
    mutationFn: () => {
      if (!form.effectiveDate) throw new Error('적용 예정일을 선택해주세요.')
      if (!form.reason.trim()) throw new Error('사유를 입력해주세요.')
      return submitWorkplaceChangeRequest({
        currentWorkplaceId: currentWorkplace.id,
        name: form.name, address: form.address, detailAddress: form.detailAddress || undefined,
        type: currentWorkplace.type, latitude: lat, longitude: lng, radiusMeters: radius,
        maxAccuracyMeters: currentWorkplace.maxAccuracyMeters,
        checkInAllowed: form.checkInAllowed, checkOutAllowed: form.checkOutAllowed,
        effectiveDate: form.effectiveDate, reason: form.reason,
      })
    },
    onSuccess: () => {
      toast.success('근무지 변경요청이 접수되었습니다. 관리자 승인 후 반영됩니다.')
      queryClient.invalidateQueries({ queryKey: ['my-workplace-change-requests'] })
      onClose()
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? e?.message ?? '신청 실패'),
  })

  return (
    <Overlay>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 500, maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 4, color: '#1e293b' }}>근무지 변경요청</h2>
        <p style={{ fontSize: 13, color: '#64748b', marginBottom: 24 }}>기존: {currentWorkplace.name}</p>
        <form onSubmit={e => { e.preventDefault(); mutation.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Field label="근무지명"><input value={form.name} onChange={e => set('name', e.target.value)} required style={inputStyle} /></Field>
          <Field label="주소">
            <div style={{ display: 'flex', gap: 8 }}>
              <input
                value={form.address}
                onChange={e => set('address', e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); searchAddress() } }}
                style={{ ...inputStyle, flex: 1 }}
                placeholder="예: 서울시 중구 을지로 1가"
              />
              <button
                type="button"
                onClick={searchAddress}
                disabled={searching}
                style={{ padding: '0 16px', background: '#f1f5f9', color: '#374151', border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 600, whiteSpace: 'nowrap' }}
              >{searching ? '검색 중...' : '주소 검색'}</button>
            </div>
          </Field>
          <Field label="상세 주소">
            <input value={form.detailAddress} onChange={e => set('detailAddress', e.target.value)} style={inputStyle} />
          </Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <Field label="위도"><input value={form.latitude} onChange={e => set('latitude', e.target.value)} required style={inputStyle} /></Field>
            <Field label="경도"><input value={form.longitude} onChange={e => set('longitude', e.target.value)} required style={inputStyle} /></Field>
          </div>
          <p style={{ fontSize: 12, color: '#94a3b8', margin: 0 }}>근무지 유형·허용 반경·허용 정확도는 관리자 승인 시 기존 설정값이 그대로 적용됩니다.</p>
          <div style={{ display: 'flex', gap: 20 }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: '#374151' }}>
              <input type="checkbox" checked={form.checkInAllowed} onChange={e => set('checkInAllowed', e.target.checked)} /> 출근 허용
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: '#374151' }}>
              <input type="checkbox" checked={form.checkOutAllowed} onChange={e => set('checkOutAllowed', e.target.checked)} /> 퇴근 허용
            </label>
          </div>
          <p style={{ fontSize: 12, color: '#64748b', margin: '2px 0 -6px' }}>지도를 클릭하거나 마커를 드래그해 좌표를 지정할 수 있습니다</p>
          <KakaoMap
            latitude={lat} longitude={lng} radiusMeters={radius} height={160}
            editable
            onPositionChange={(newLat, newLng) => setForm(p => ({ ...p, latitude: String(newLat), longitude: String(newLng) }))}
          />
          <Field label="적용 예정일">
            <input type="date" value={form.effectiveDate} onChange={e => set('effectiveDate', e.target.value)} required style={inputStyle} />
          </Field>
          <Field label="사유">
            <textarea value={form.reason} onChange={e => set('reason', e.target.value)} required rows={3} style={{ ...inputStyle, resize: 'vertical' }} />
          </Field>
          <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
            <button type="button" onClick={onClose} style={cancelBtnStyle}>취소</button>
            <button type="submit" disabled={mutation.isPending} style={primaryBtnStyle}>{mutation.isPending ? '신청 중...' : '근무지변경요청'}</button>
          </div>
        </form>
      </div>
    </Overlay>
  )
}

// ─── 메인 페이지 ────────────────────────────────────────────
export default function WorkplacesPage() {
  const [showCreate, setShowCreate] = useState(false)
  const [editTarget, setEditTarget] = useState<Workplace | null>(null)
  const [assignTarget, setAssignTarget] = useState<Workplace | null>(null)
  const [changeRequestTarget, setChangeRequestTarget] = useState<Workplace | null>(null)
  const [showInactive, setShowInactive] = useState(false)
  const queryClient = useQueryClient()
  const role = useAuthStore(s => s.role)
  const { isActionEnabled } = usePermissions()
  // 일반 직원(role=EMPLOYEE)은 전체 근무지 목록 조회 권한이 없다(서버 GET /workplaces가 MANAGER 이상만 허용) —
  // 본인이 배정된 근무지만 보여주고, 등록·수정·배정·삭제는 아예 노출하지 않는다. 대신 변경요청만 가능하다.
  const isPlainEmployee = role === 'EMPLOYEE'
  const canCreate = !isPlainEmployee && isActionEnabled('workplaces', 'CREATE')
  // 수정·직원배정관리는 서버에서 HR_ADMIN/SYSTEM_ADMIN만 허용하므로 그 기준을 그대로 따른다.
  const canEdit = !isPlainEmployee && isActionEnabled('workplaces', 'EDIT')

  const { data: workplaces = [], isLoading } = useQuery({
    queryKey: isPlainEmployee ? ['workplaces', 'assigned'] : ['workplaces', showInactive],
    queryFn: () => isPlainEmployee
      ? getMyAssignedWorkplaces().then(r => r.data.data)
      : getWorkplaces(COMPANY_ID, showInactive).then(r => r.data.data),
  })

  const { data: myChangeRequests = [] } = useQuery({
    queryKey: ['my-workplace-change-requests'],
    queryFn: () => getMyWorkplaceChangeRequests().then(r => r.data.data),
    enabled: isPlainEmployee,
  })
  const pendingChangeRequestByWorkplaceId = new Map(
    myChangeRequests.filter(r => r.status === 'PENDING' && r.currentWorkplaceId != null).map(r => [r.currentWorkplaceId as number, r]),
  )
  // 검토중이 아니어도(승인/반려) 가장 최근 변경요청 상태를 근무지 카드에 표시한다.
  // 승인된 경우 적용예정일 전까지는 기존 근무지 카드가 계속 보이므로, 그 카드에 "며칠부터 자동 전환" 안내를 함께 보여준다.
  const latestChangeRequestByWorkplaceId = new Map<number, WorkplaceChangeRequest>()
  for (const r of myChangeRequests) {
    if (r.currentWorkplaceId == null) continue
    const existing = latestChangeRequestByWorkplaceId.get(r.currentWorkplaceId)
    if (!existing || new Date(r.createdAt).getTime() > new Date(existing.createdAt).getTime()) {
      latestChangeRequestByWorkplaceId.set(r.currentWorkplaceId, r)
    }
  }

  // 삭제(비활성화)·복구는 백엔드 권한 정책과 동일하게 SYSTEM_ADMIN만 가능
  const canManage = role === 'SYSTEM_ADMIN'

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deactivateWorkplace(id),
    onSuccess: () => { toast.success('근무지가 삭제(비활성화)되었습니다.'); queryClient.invalidateQueries({ queryKey: ['workplaces'] }) },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '삭제 실패'),
  })

  const restoreMutation = useMutation({
    mutationFn: (id: number) => activateWorkplace(id),
    onSuccess: () => { toast.success('근무지가 복구되었습니다.'); queryClient.invalidateQueries({ queryKey: ['workplaces'] }) },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '복구 실패'),
  })

  const handleDelete = (w: Workplace) => {
    if (confirm(`"${w.name}" 근무지를 삭제하시겠습니까?\n배정된 직원의 출퇴근에 더 이상 사용할 수 없게 됩니다.`)) {
      deleteMutation.mutate(w.id)
    }
  }

  const handleRestore = (w: Workplace) => {
    if (confirm(`"${w.name}" 근무지를 복구하시겠습니까?`)) {
      restoreMutation.mutate(w.id)
    }
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, color: '#1e293b' }}>근무지 관리</h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          {canManage && (
            <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: '#64748b', cursor: 'pointer' }}>
              <input type="checkbox" checked={showInactive} onChange={e => setShowInactive(e.target.checked)} />
              삭제된 근무지 포함
            </label>
          )}
          {canCreate && <button onClick={() => setShowCreate(true)} style={primaryBtnStyle}>+ 근무지 등록</button>}
        </div>
      </div>

      {isLoading && <p style={{ color: '#64748b' }}>로딩 중...</p>}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 20 }}>
        {workplaces.map((w: Workplace) => (
          <WorkplaceCard
            key={w.id}
            workplace={w}
            onAssign={() => setAssignTarget(w)}
            onEdit={() => setEditTarget(w)}
            onDelete={() => handleDelete(w)}
            onRestore={() => handleRestore(w)}
            canManage={canManage}
            canEdit={canEdit}
            canRequestChange={isPlainEmployee}
            onRequestChange={() => setChangeRequestTarget(w)}
            pendingChangeRequest={pendingChangeRequestByWorkplaceId.get(w.id)}
            latestChangeRequest={latestChangeRequestByWorkplaceId.get(w.id)}
          />
        ))}
        {!isLoading && workplaces.length === 0 && (
          <div style={{ gridColumn: '1 / -1', padding: 48, textAlign: 'center', color: '#64748b', background: '#fff', borderRadius: 12 }}>
            등록된 근무지가 없습니다.
          </div>
        )}
      </div>

      {showCreate && <CreateWorkplaceModal onClose={() => setShowCreate(false)} />}
      {editTarget && <EditWorkplaceModal workplace={editTarget} onClose={() => setEditTarget(null)} />}
      {assignTarget && <AssignUserModal workplace={assignTarget} onClose={() => setAssignTarget(null)} />}
      {changeRequestTarget && <WorkplaceChangeRequestModal currentWorkplace={changeRequestTarget} onClose={() => setChangeRequestTarget(null)} />}
    </div>
  )
}

// ─── 공용 스타일 헬퍼 ───────────────────────────────────────
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
const inputStyle: React.CSSProperties = { width: '100%', padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14 }
const cancelBtnStyle: React.CSSProperties = { flex: 1, padding: 10, border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontSize: 14, background: '#fff', color: '#374151' }
const primaryBtnStyle: React.CSSProperties = { padding: '8px 18px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontWeight: 600, fontSize: 14 }
