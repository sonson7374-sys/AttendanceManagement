import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getUsers, createUser, lockUser, unlockUser, bulkCreateUsers, downloadBulkImportTemplate,
  updateUserProfile, changeUserRole, resignUser, deleteUser, resetUserPassword, setUserPassword, listUserDevices, revokeUserDevice,
} from '@/api/users'
import type { BulkUserImportResponse } from '@/api/users'
import { getOrganizations } from '@/api/organizations'
import { getWorkplaces, getWorkplacesForUser, assignUserToWorkplace, removeUserFromWorkplace } from '@/api/workplaces'
import { getWorkSchedules, getCurrentWorkScheduleForUser, assignWorkScheduleToUser } from '@/api/schedules'
import type { User, UserRole, Organization, UserDevice, Workplace, WorkSchedule } from '@/types'
import { useAuthStore } from '@/store/authStore'
import { usePermissions } from '@/hooks/usePermissions'
import { useLevelOptions } from '@/hooks/useLevelOptions'
import { orgOptionLabel, buildOrgsById, sortOrgsHierarchically } from '@/utils/organizations'
import toast from 'react-hot-toast'

const ROLE_LABEL: Record<UserRole, string> = {
  EMPLOYEE: '직원',
  MANAGER: '관리자',
  HR_ADMIN: 'HR 관리자',
  SYSTEM_ADMIN: '시스템 관리자',
}

const STATUS_COLOR = { ACTIVE: '#10b981', INACTIVE: '#94a3b8', LOCKED: '#ef4444' }
const STATUS_LABEL = { ACTIVE: '활성', INACTIVE: '비활성', LOCKED: '잠금' }

interface CreateFormData {
  email: string; password: string; name: string
  employeeNumber: string; role: UserRole; level: string
}

function CreateModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()
  const levelOptions = useLevelOptions()
  const [form, setForm] = useState<CreateFormData>({
    email: '', password: '', name: '', employeeNumber: '',
    role: 'EMPLOYEE', level: '',
  })
  const [loading, setLoading] = useState(false)

  const set = (k: keyof CreateFormData, v: string | number) =>
    setForm((prev) => ({ ...prev, [k]: v }))

  useEffect(() => {
    if (!form.level && levelOptions.length > 0) {
      set('level', levelOptions[0].value)
    }
  }, [levelOptions])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    try {
      await createUser(form)
      toast.success('직원이 등록되었습니다.')
      queryClient.invalidateQueries({ queryKey: ['users'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      onClose()
    } catch (err: any) {
      toast.error(err?.response?.data?.message ?? '등록 실패')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100,
    }}>
      <div style={{
        background: '#fff', borderRadius: 12, padding: 32,
        width: 440, boxShadow: '0 8px 32px rgba(0,0,0,0.12)',
      }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 24, color: '#1e293b' }}>직원 등록</h2>
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {([
            ['email', '이메일', 'email'],
            ['password', '비밀번호', 'password'],
            ['name', '이름', 'text'],
            ['employeeNumber', '사번', 'text'],
          ] as [keyof CreateFormData, string, string][]).map(([key, label, type]) => (
            <div key={key}>
              <label style={labelStyle}>{label}</label>
              <input
                type={type}
                value={form[key] as string}
                onChange={(e) => set(key, e.target.value)}
                required style={{ ...inputStyle, width: '100%' }}
              />
            </div>
          ))}
          <div>
            <label style={labelStyle}>권한레벨</label>
            <select
              value={form.level}
              onChange={(e) => set('level', e.target.value)}
              style={{ ...inputStyle, width: '100%' }}
            >
              {levelOptions.map(({ value, label }) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>
          <div>
            <label style={labelStyle}>역할</label>
            <select
              value={form.role}
              onChange={(e) => set('role', e.target.value as UserRole)}
              style={{ ...inputStyle, width: '100%' }}
            >
              {Object.entries(ROLE_LABEL).map(([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ))}
            </select>
          </div>
          <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
            <button type="button" onClick={onClose} style={{
              flex: 1, padding: 10, border: '1px solid #d1d5db', borderRadius: 8,
              cursor: 'pointer', fontSize: 14, background: '#fff', color: '#374151',
            }}>취소</button>
            <button type="submit" disabled={loading} style={{
              flex: 1, padding: 10, background: '#2563eb', color: '#fff',
              border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 14, fontWeight: 600,
            }}>{loading ? '등록 중...' : '등록'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

interface EditFormData {
  name: string; employeeNumber: string; phone: string; jobTitle: string
  employmentType: string; hireDate: string; organizationId: string; role: UserRole; level: string
}

function EditModal({ user, canChangeRole, canChangeLevel, onClose }: {
  user: User; canChangeRole: boolean; canChangeLevel: boolean; onClose: () => void
}) {
  const queryClient = useQueryClient()
  const levelOptions = useLevelOptions()
  const [form, setForm] = useState<EditFormData>({
    name: user.name,
    employeeNumber: user.employeeNumber ?? '',
    phone: user.phone ?? '',
    jobTitle: user.jobTitle ?? '',
    employmentType: user.employmentType ?? '',
    hireDate: user.hireDate ?? '',
    organizationId: user.organizationId ? String(user.organizationId) : '',
    role: user.role,
    level: user.level ?? '',
  })
  const [loading, setLoading] = useState(false)

  const { data: organizations = [] } = useQuery({
    queryKey: ['organizations'],
    queryFn: () => getOrganizations().then(r => r.data.data),
  })
  const orgsById = buildOrgsById(organizations)
  const sortedOrganizations = sortOrgsHierarchically(organizations)

  const set = (k: keyof EditFormData, v: string) => setForm((prev) => ({ ...prev, [k]: v }))

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    try {
      await updateUserProfile(user.id, {
        name: form.name,
        employeeNumber: form.employeeNumber || undefined,
        phone: form.phone || undefined,
        jobTitle: form.jobTitle || undefined,
        employmentType: form.employmentType || undefined,
        hireDate: form.hireDate || undefined,
        organizationId: form.organizationId ? Number(form.organizationId) : undefined,
        level: form.level,
      })
      if (canChangeRole && form.role !== user.role) {
        await changeUserRole(user.id, form.role)
      }
      toast.success('직원 정보가 수정되었습니다.')
      queryClient.invalidateQueries({ queryKey: ['users'] })
      onClose()
    } catch (err: any) {
      toast.error(err?.response?.data?.message ?? '수정 실패')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100,
    }}>
      <div style={{
        background: '#fff', borderRadius: 12, padding: 32,
        width: 460, maxHeight: '85vh', overflowY: 'auto', boxShadow: '0 8px 32px rgba(0,0,0,0.12)',
      }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 4, color: '#1e293b' }}>직원 정보 수정</h2>
        <p style={{ fontSize: 13, color: '#64748b', marginBottom: 20 }}>{user.email} (이메일은 변경할 수 없습니다)</p>
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={labelStyle}>이름</label>
            <input value={form.name} onChange={(e) => set('name', e.target.value)} required style={{ ...inputStyle, width: '100%' }} />
          </div>
          <div>
            <label style={labelStyle}>사번</label>
            <input value={form.employeeNumber} onChange={(e) => set('employeeNumber', e.target.value)} style={{ ...inputStyle, width: '100%' }} />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <div>
              <label style={labelStyle}>휴대전화</label>
              <input value={form.phone} onChange={(e) => set('phone', e.target.value)} style={{ ...inputStyle, width: '100%' }} />
            </div>
            <div>
              <label style={labelStyle}>직급</label>
              <input value={form.jobTitle} onChange={(e) => set('jobTitle', e.target.value)} style={{ ...inputStyle, width: '100%' }} />
            </div>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <div>
              <label style={labelStyle}>고용형태</label>
              <input value={form.employmentType} onChange={(e) => set('employmentType', e.target.value)} style={{ ...inputStyle, width: '100%' }} placeholder="예: 정규직" />
            </div>
            <div>
              <label style={labelStyle}>입사일</label>
              <input type="date" value={form.hireDate} onChange={(e) => set('hireDate', e.target.value)} style={{ ...inputStyle, width: '100%' }} />
            </div>
          </div>
          <div>
            <label style={labelStyle}>소속 조직</label>
            <select value={form.organizationId} onChange={(e) => set('organizationId', e.target.value)} style={{ ...inputStyle, width: '100%' }}>
              <option value="">선택 안 함</option>
              {sortedOrganizations.map((o: Organization) => (
                <option key={o.id} value={o.id}>{orgOptionLabel(o, orgsById)}</option>
              ))}
            </select>
          </div>
          <div>
            <label style={labelStyle}>권한레벨{!canChangeLevel && ' (인사담당자 이상만 변경 가능)'}</label>
            <select
              value={form.level}
              onChange={(e) => set('level', e.target.value)}
              disabled={!canChangeLevel}
              style={{ ...inputStyle, width: '100%', opacity: canChangeLevel ? 1 : 0.6 }}
            >
              {levelOptions.map(({ value, label }) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>
          <div>
            <label style={labelStyle}>역할{!canChangeRole && ' (시스템 관리자만 변경 가능)'}</label>
            <select
              value={form.role}
              onChange={(e) => set('role', e.target.value as UserRole)}
              disabled={!canChangeRole}
              style={{ ...inputStyle, width: '100%', opacity: canChangeRole ? 1 : 0.6 }}
            >
              {Object.entries(ROLE_LABEL).map(([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ))}
            </select>
          </div>
          <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
            <button type="button" onClick={onClose} style={{
              flex: 1, padding: 10, border: '1px solid #d1d5db', borderRadius: 8,
              cursor: 'pointer', fontSize: 14, background: '#fff', color: '#374151',
            }}>취소</button>
            <button type="submit" disabled={loading} style={{
              flex: 1, padding: 10, background: '#2563eb', color: '#fff',
              border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 14, fontWeight: 600,
            }}>{loading ? '저장 중...' : '저장'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

function BulkImportModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [result, setResult] = useState<BulkUserImportResponse | null>(null)

  const handleUpload = async () => {
    if (!file) { toast.error('엑셀 파일을 선택해주세요.'); return }
    setUploading(true)
    setResult(null)
    try {
      const res = await bulkCreateUsers(file)
      setResult(res.data.data)
      queryClient.invalidateQueries({ queryKey: ['users'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      if (res.data.data.failureCount === 0) {
        toast.success(`${res.data.data.successCount}명 등록되었습니다.`)
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
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100,
    }}>
      <div style={{
        background: '#fff', borderRadius: 12, padding: 32,
        width: 560, maxHeight: '85vh', overflowY: 'auto', boxShadow: '0 8px 32px rgba(0,0,0,0.12)',
      }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 8, color: '#1e293b' }}>직원 일괄 등록</h2>
        <p style={{ fontSize: 13, color: '#64748b', marginBottom: 20 }}>
          엑셀 템플릿을 내려받아 형식에 맞게 작성한 후 업로드해주세요. (이메일·비밀번호·이름은 필수)
        </p>

        <button
          type="button"
          onClick={() => downloadBulkImportTemplate()}
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
                  {r.rowNumber}행 {r.email || '(이메일 없음)'} — {r.message}
                </div>
              ))}
            </div>
          </div>
        )}

        <div style={{ display: 'flex', gap: 10 }}>
          <button type="button" onClick={onClose} style={{
            flex: 1, padding: 10, border: '1px solid #d1d5db', borderRadius: 8,
            cursor: 'pointer', fontSize: 14, background: '#fff', color: '#374151',
          }}>닫기</button>
          <button type="button" onClick={handleUpload} disabled={uploading || !file} style={{
            flex: 1, padding: 10, background: '#2563eb', color: '#fff',
            border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 14, fontWeight: 600,
            opacity: (uploading || !file) ? 0.6 : 1,
          }}>{uploading ? '업로드 중...' : '업로드'}</button>
        </div>
      </div>
    </div>
  )
}

function DeviceModal({ user, onClose }: { user: User; onClose: () => void }) {
  const queryClient = useQueryClient()
  const { data: devices = [], isLoading } = useQuery({
    queryKey: ['user-devices', user.id],
    queryFn: () => listUserDevices(user.id).then(r => r.data.data),
  })

  const revoke = useMutation({
    mutationFn: (deviceId: string) => revokeUserDevice(user.id, deviceId),
    onSuccess: () => {
      toast.success('단말기가 해제되었습니다.')
      queryClient.invalidateQueries({ queryKey: ['user-devices', user.id] })
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '해제 실패'),
  })

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100 }}>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 480, maxHeight: '85vh', overflowY: 'auto', boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 4, color: '#1e293b' }}>등록 단말기</h2>
        <p style={{ fontSize: 13, color: '#64748b', marginBottom: 20 }}>{user.name} ({user.employeeNumber})</p>

        {isLoading ? (
          <p style={{ color: '#64748b' }}>로딩 중...</p>
        ) : devices.length === 0 ? (
          <p style={{ fontSize: 13, color: '#94a3b8', padding: '16px 0' }}>등록된 단말기가 없습니다.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {devices.map((d: UserDevice) => (
              <div key={d.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 12px', background: '#f8fafc', borderRadius: 8 }}>
                <div>
                  <p style={{ fontSize: 13, fontWeight: 500, color: '#1e293b' }}>{d.deviceName ?? d.deviceId} <span style={{ fontSize: 11, color: '#94a3b8' }}>({d.devicePlatform ?? '알 수 없음'})</span></p>
                  <p style={{ fontSize: 11, color: '#94a3b8' }}>
                    {d.active ? '사용 중' : '해제됨'} · 마지막 접속 {d.lastSeenAt ? new Date(d.lastSeenAt).toLocaleString() : '-'}
                  </p>
                </div>
                {d.active && (
                  <button
                    onClick={() => revoke.mutate(d.deviceId)}
                    disabled={revoke.isPending}
                    style={{ padding: '4px 10px', fontSize: 12, color: '#ef4444', border: '1px solid #fca5a5', borderRadius: 6, background: '#fff', cursor: 'pointer' }}
                  >
                    해제
                  </button>
                )}
              </div>
            ))}
          </div>
        )}

        <button onClick={onClose} style={{ ...pageBtnStyle, width: '100%', marginTop: 20 }}>닫기</button>
      </div>
    </div>
  )
}

function AssignmentModal({ user, onClose }: { user: User; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [selectedScheduleId, setSelectedScheduleId] = useState<number | ''>('')

  const { data: allWorkplaces = [] } = useQuery({
    queryKey: ['workplaces', false],
    queryFn: () => getWorkplaces().then(r => r.data.data),
  })
  const { data: assignedWorkplaces = [], isLoading: workplacesLoading } = useQuery({
    queryKey: ['user-workplaces', user.id],
    queryFn: () => getWorkplacesForUser(user.id).then(r => r.data.data),
  })
  const assignedWorkplaceIds = new Set(assignedWorkplaces.map((w: Workplace) => w.id))

  const { data: allSchedules = [] } = useQuery({
    queryKey: ['work-schedules'],
    queryFn: () => getWorkSchedules().then(r => r.data.data),
  })
  const { data: currentSchedule, isLoading: scheduleLoading } = useQuery({
    queryKey: ['user-work-schedule', user.id],
    queryFn: () => getCurrentWorkScheduleForUser(user.id).then(r => r.data.data),
  })

  const toggleWorkplace = useMutation({
    mutationFn: (workplace: Workplace) =>
      assignedWorkplaceIds.has(workplace.id)
        ? removeUserFromWorkplace(workplace.id, user.id)
        : assignUserToWorkplace(workplace.id, user.id),
    onSuccess: () => {
      toast.success('근무지 배정이 변경되었습니다.')
      queryClient.invalidateQueries({ queryKey: ['user-workplaces', user.id] })
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '근무지 배정 변경 실패'),
  })

  const assignSchedule = useMutation({
    mutationFn: (workScheduleId: number) => assignWorkScheduleToUser(user.id, workScheduleId),
    onSuccess: () => {
      toast.success('근무제가 변경되었습니다.')
      queryClient.invalidateQueries({ queryKey: ['user-work-schedule', user.id] })
      setSelectedScheduleId('')
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '근무제 변경 실패'),
  })

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100 }}>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 480, maxHeight: '85vh', overflowY: 'auto', boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 4, color: '#1e293b' }}>근무지 · 근무제 배정</h2>
        <p style={{ fontSize: 13, color: '#64748b', marginBottom: 24 }}>{user.name} ({user.employeeNumber})</p>

        <div style={{ marginBottom: 24 }}>
          <p style={{ fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 8 }}>근무제</p>
          {scheduleLoading ? (
            <p style={{ fontSize: 13, color: '#64748b' }}>로딩 중...</p>
          ) : (
            <>
              <p style={{ fontSize: 13, color: '#1e293b', marginBottom: 10 }}>
                현재 적용 근무제: <strong>{currentSchedule?.name ?? '-'}</strong>
              </p>
              <div style={{ display: 'flex', gap: 8 }}>
                <select
                  value={selectedScheduleId}
                  onChange={e => setSelectedScheduleId(Number(e.target.value) || '')}
                  style={{ flex: 1, padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14 }}
                >
                  <option value="">근무제 선택...</option>
                  {allSchedules.map((s: WorkSchedule) => (
                    <option key={s.id} value={s.id}>{s.name}</option>
                  ))}
                </select>
                <button
                  onClick={() => selectedScheduleId && assignSchedule.mutate(selectedScheduleId as number)}
                  disabled={!selectedScheduleId || assignSchedule.isPending}
                  style={{ padding: '8px 16px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 14, fontWeight: 600, opacity: !selectedScheduleId ? 0.5 : 1 }}
                >
                  변경
                </button>
              </div>
            </>
          )}
        </div>

        <div>
          <p style={{ fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 8 }}>
            근무지 ({assignedWorkplaces.length}개 배정됨)
          </p>
          {workplacesLoading ? (
            <p style={{ fontSize: 13, color: '#64748b' }}>로딩 중...</p>
          ) : allWorkplaces.length === 0 ? (
            <p style={{ fontSize: 13, color: '#94a3b8', padding: '16px 0' }}>등록된 근무지가 없습니다.</p>
          ) : (
            <div style={{ maxHeight: 260, overflowY: 'auto', border: '1px solid #e2e8f0', borderRadius: 8, padding: 8 }}>
              {allWorkplaces.map((w: Workplace) => (
                <label key={w.id} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 4px', fontSize: 13, color: '#374151', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={assignedWorkplaceIds.has(w.id)}
                    disabled={toggleWorkplace.isPending}
                    onChange={() => toggleWorkplace.mutate(w)}
                  />
                  {w.name}
                  {!w.active && <span style={{ fontSize: 11, color: '#94a3b8' }}>(비활성)</span>}
                </label>
              ))}
            </div>
          )}
        </div>

        <button onClick={onClose} style={{ ...pageBtnStyle, width: '100%', marginTop: 20 }}>닫기</button>
      </div>
    </div>
  )
}

// 시스템관리자가 현재 비밀번호 확인 없이 새 비밀번호만 입력해 직접 지정한다.
// 관리자가 값을 직접 지정하는 것이므로, 다음 로그인 시 비밀번호 변경이 강제된다.
function SetPasswordModal({ user, onClose }: { user: User; onClose: () => void }) {
  const [newPassword, setNewPassword] = useState('')

  const mutation = useMutation({
    mutationFn: () => setUserPassword(user.id, newPassword),
    onSuccess: () => {
      toast.success('비밀번호가 변경되었습니다. 직원은 다음 로그인 시 비밀번호를 다시 설정해야 합니다.')
      onClose()
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '비밀번호 변경 실패'),
  })

  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100,
    }}>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 400, boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 4, color: '#1e293b' }}>비밀번호 변경</h2>
        <p style={{ fontSize: 13, color: '#64748b', marginBottom: 20 }}>{user.name} ({user.employeeNumber})</p>
        <form onSubmit={e => { e.preventDefault(); mutation.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={labelStyle}>새 비밀번호</label>
            <input
              type="password"
              value={newPassword}
              onChange={e => setNewPassword(e.target.value)}
              required
              minLength={8}
              autoFocus
              style={{ ...inputStyle, width: '100%' }}
              placeholder="8자 이상"
            />
          </div>
          <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
            <button type="button" onClick={onClose} style={{ ...pageBtnStyle, flex: 1 }}>취소</button>
            <button
              type="submit"
              disabled={mutation.isPending}
              style={{
                flex: 1, padding: '8px 18px', background: '#2563eb', color: '#fff',
                border: 'none', borderRadius: 8, cursor: 'pointer', fontWeight: 600, fontSize: 14,
                opacity: mutation.isPending ? 0.6 : 1,
              }}
            >
              {mutation.isPending ? '변경 중...' : '변경'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function EmployeesPage() {
  const [page, setPage] = useState(0)
  const [organizationId, setOrganizationId] = useState('')
  const [nameSearch, setNameSearch] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [showBulkImport, setShowBulkImport] = useState(false)
  const [editTarget, setEditTarget] = useState<User | null>(null)
  const [deviceTarget, setDeviceTarget] = useState<User | null>(null)
  const [assignmentTarget, setAssignmentTarget] = useState<User | null>(null)
  const [passwordTarget, setPasswordTarget] = useState<User | null>(null)
  const queryClient = useQueryClient()
  const role = useAuthStore(s => s.role)
  const level = useAuthStore(s => s.level)
  const canChangeRole = role === 'SYSTEM_ADMIN'
  const canManageEmployment = role === 'HR_ADMIN' || role === 'SYSTEM_ADMIN'
  // 근무지·근무제 배정 화면(사번/이름 클릭)은 권한레벨(LEVEL_ROLL)이 시스템 관리자(SYSADMIN)일 때만 연다.
  const canAssign = level === 'SYSADMIN'
  // 완전 삭제 버튼은 권한레벨(LEVEL_ROLL)이 시스템 관리자(SYSADMIN)이면서, 대상이 이미
  // 퇴사 처리(INACTIVE)된 직원일 때만 보인다.
  const canDelete = level === 'SYSADMIN'
  const { isActionEnabled } = usePermissions()
  const canCreate = isActionEnabled('employees', 'CREATE')
  const canBulkCreate = isActionEnabled('employees', 'BULK_CREATE')
  const canEdit = isActionEnabled('employees', 'EDIT')
  const levelOptions = useLevelOptions()
  const levelLabel: Record<string, string> = Object.fromEntries(levelOptions.map(o => [o.value, o.label]))

  const { data: organizations = [] } = useQuery({
    queryKey: ['organizations'],
    queryFn: () => getOrganizations().then(r => r.data.data),
  })
  const orgsById = buildOrgsById(organizations)
  const sortedOrganizations = sortOrgsHierarchically(organizations)

  const filters = {
    organizationId: organizationId ? Number(organizationId) : undefined,
    name: nameSearch || undefined,
  }

  const { data, isLoading } = useQuery({
    queryKey: ['users', page, filters],
    queryFn: () => getUsers(page, 20, filters).then((r) => r.data.data),
  })

  const handleOrganizationChange = (v: string) => { setOrganizationId(v); setPage(0) }
  const handleNameSearchChange = (v: string) => { setNameSearch(v); setPage(0) }

  const toggleLock = useMutation({
    mutationFn: ({ id, locked }: { id: number; locked: boolean }) =>
      locked ? unlockUser(id) : lockUser(id),
    onSuccess: () => {
      toast.success('처리되었습니다.')
      queryClient.invalidateQueries({ queryKey: ['users'] })
    },
  })

  const resign = useMutation({
    mutationFn: ({ id, resignDate }: { id: number; resignDate: string }) => resignUser(id, resignDate),
    onSuccess: () => {
      toast.success('퇴사 처리되었습니다.')
      queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '퇴사 처리 실패'),
  })

  const resetPassword = useMutation({
    mutationFn: (id: number) => resetUserPassword(id),
    onSuccess: (res) => {
      const { temporaryPassword } = res.data.data
      window.alert(`임시 비밀번호가 발급되었습니다.\n\n${temporaryPassword}\n\n직원에게 안전한 방법으로 전달한 뒤 최초 로그인 시 비밀번호 변경을 안내해주세요.`)
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '비밀번호 초기화 실패'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteUser(id),
    onSuccess: () => {
      toast.success('삭제되었습니다.')
      queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '삭제 실패'),
  })

  const handleResign = (u: User) => {
    const resignDate = window.prompt(`"${u.name}"님의 퇴사일을 입력해주세요 (YYYY-MM-DD)`, new Date().toISOString().slice(0, 10))
    if (resignDate) resign.mutate({ id: u.id, resignDate })
  }

  const handleDelete = (u: User) => {
    if (confirm(`"${u.name}"(${u.employeeNumber}) 계정과 본인의 출퇴근·신청·기기·알림 등 모든 이력을 DB에서 완전히 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.`)) {
      deleteMutation.mutate(u.id)
    }
  }

  const users = data?.content ?? []

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, color: '#1e293b' }}>직원 관리</h1>
        <div style={{ display: 'flex', gap: 8 }}>
          {canBulkCreate && (
            <button
              onClick={() => setShowBulkImport(true)}
              style={{
                padding: '8px 18px', background: '#fff', color: '#374151',
                border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontWeight: 600, fontSize: 14,
              }}
            >
              일괄 등록
            </button>
          )}
          {canCreate && (
            <button
              onClick={() => setShowCreate(true)}
              style={{
                padding: '8px 18px', background: '#2563eb', color: '#fff',
                border: 'none', borderRadius: 8, cursor: 'pointer', fontWeight: 600, fontSize: 14,
              }}
            >
              + 직원 등록
            </button>
          )}
        </div>
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 16 }}>
        <select value={organizationId} onChange={(e) => handleOrganizationChange(e.target.value)} style={{ ...inputStyle, width: 180 }}>
          <option value="">부서 전체</option>
          {sortedOrganizations.map((o) => <option key={o.id} value={o.id}>{orgOptionLabel(o, orgsById)}</option>)}
        </select>
        <input
          value={nameSearch}
          onChange={(e) => handleNameSearchChange(e.target.value)}
          placeholder="이름 검색"
          style={{ ...inputStyle, width: 200 }}
        />
      </div>

      <div style={{
        background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', overflow: 'hidden',
      }}>
        {isLoading ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>로딩 중...</div>
        ) : users.length === 0 ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>직원이 없습니다.</div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#f8fafc' }}>
                {['사번', '이름', '이메일', '권한', '역할', '상태', '관리'].map((h) => (
                  <th key={h} style={{
                    padding: '12px 16px', textAlign: 'left',
                    fontSize: 12, fontWeight: 600, color: '#64748b',
                    borderBottom: '1px solid #e2e8f0',
                  }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {users.map((u: User) => (
                <tr key={u.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                  <td
                    style={canAssign ? { ...tdStyle, cursor: 'pointer', color: '#2563eb' } : tdStyle}
                    onClick={canAssign ? () => setAssignmentTarget(u) : undefined}
                    title={canAssign ? '근무지·근무제 배정' : undefined}
                  >
                    {u.employeeNumber}
                  </td>
                  <td
                    style={canAssign ? { ...tdStyle, fontWeight: 500, cursor: 'pointer', color: '#2563eb' } : { ...tdStyle, fontWeight: 500 }}
                    onClick={canAssign ? () => setAssignmentTarget(u) : undefined}
                    title={canAssign ? '근무지·근무제 배정' : undefined}
                  >
                    {u.name}
                  </td>
                  <td style={tdStyle}>{u.email}</td>
                  <td style={tdStyle}>{levelLabel[u.level] ?? u.level}</td>
                  <td style={tdStyle}>{ROLE_LABEL[u.role]}</td>
                  <td style={tdStyle}>
                    <span style={{
                      padding: '3px 10px', borderRadius: 20, fontSize: 12,
                      background: STATUS_COLOR[u.status] + '20',
                      color: STATUS_COLOR[u.status], fontWeight: 600,
                    }}>
                      {STATUS_LABEL[u.status]}
                    </span>
                    {u.status === 'INACTIVE' && u.resignDate && (
                      <span style={{ fontSize: 11, color: '#94a3b8', marginLeft: 6 }}>({u.resignDate})</span>
                    )}
                  </td>
                  <td style={tdStyle}>
                    <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                      {canEdit && (
                        <button onClick={() => setEditTarget(u)} style={rowBtnStyle('#374151')}>
                          수정
                        </button>
                      )}
                      {/* 목록 자체가 이미 조회범위(본인 또는 파트장 이상이 관리하는 하위직원)로 제한되어 있으므로,
                          여기 보이는 모든 행에 대해 비밀번호 변경이 가능하다 — 그 외 관리기능은 인사담당자 이상만. */}
                      <button onClick={() => setPasswordTarget(u)} style={rowBtnStyle('#374151')}>
                        비밀번호 변경
                      </button>
                      {canManageEmployment && (
                        <>
                          <button
                            onClick={() => toggleLock.mutate({ id: u.id, locked: u.status === 'LOCKED' })}
                            style={rowBtnStyle(u.status === 'LOCKED' ? '#10b981' : '#ef4444')}
                          >
                            {u.status === 'LOCKED' ? '잠금 해제' : '잠금'}
                          </button>
                          <button onClick={() => setDeviceTarget(u)} style={rowBtnStyle('#374151')}>
                            단말기
                          </button>
                          <button onClick={() => resetPassword.mutate(u.id)} disabled={resetPassword.isPending} style={rowBtnStyle('#374151')}>
                            비밀번호 초기화
                          </button>
                          {u.status !== 'INACTIVE' && (
                            <button onClick={() => handleResign(u)} style={rowBtnStyle('#ef4444')}>
                              퇴사 처리
                            </button>
                          )}
                        </>
                      )}
                      {canDelete && u.status === 'INACTIVE' && (
                        <button onClick={() => handleDelete(u)} disabled={deleteMutation.isPending} style={rowBtnStyle('#ef4444')}>
                          삭제
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div style={{ display: 'flex', gap: 8, marginTop: 16, justifyContent: 'center' }}>
          <button disabled={page === 0} onClick={() => setPage(0)} style={pageBtnStyle}>처음</button>
          <button disabled={page === 0} onClick={() => setPage((p) => p - 1)} style={pageBtnStyle}>이전</button>
          <span style={{ padding: '6px 12px', fontSize: 13 }}>
            {page + 1} / {data.totalPages}
          </span>
          <button disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)} style={pageBtnStyle}>다음</button>
        </div>
      )}

      {showCreate && <CreateModal onClose={() => setShowCreate(false)} />}
      {showBulkImport && <BulkImportModal onClose={() => setShowBulkImport(false)} />}
      {editTarget && (
        <EditModal user={editTarget} canChangeRole={canChangeRole} canChangeLevel={canManageEmployment} onClose={() => setEditTarget(null)} />
      )}
      {deviceTarget && <DeviceModal user={deviceTarget} onClose={() => setDeviceTarget(null)} />}
      {assignmentTarget && <AssignmentModal user={assignmentTarget} onClose={() => setAssignmentTarget(null)} />}
      {passwordTarget && <SetPasswordModal user={passwordTarget} onClose={() => setPasswordTarget(null)} />}
    </div>
  )
}

const labelStyle: React.CSSProperties = { display: 'block', fontSize: 13, fontWeight: 500, color: '#374151', marginBottom: 4 }
const inputStyle: React.CSSProperties = { padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14 }
const tdStyle: React.CSSProperties = { padding: '12px 16px', fontSize: 14, color: '#374151' }
const pageBtnStyle: React.CSSProperties = {
  padding: '6px 12px', border: '1px solid #d1d5db', borderRadius: 6,
  cursor: 'pointer', fontSize: 13, background: '#fff',
}
const rowBtnStyle = (color: string): React.CSSProperties => ({
  padding: '4px 12px', fontSize: 12, cursor: 'pointer',
  border: '1px solid #d1d5db', borderRadius: 6, background: '#fff', color,
})
