import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getCommonCodes, createCommonCode, updateCommonCode, deleteCommonCode } from '@/api/commonCodes'
import type { CommonCode } from '@/api/commonCodes'
import { getCommonCodeGroups, createCommonCodeGroup, updateCommonCodeGroup } from '@/api/commonCodeGroups'
import type { CommonCodeGroup } from '@/api/commonCodeGroups'
import { getMenuPermissionsByRole, upsertMenuPermission } from '@/api/menuPermissions'
import toast from 'react-hot-toast'

// 로그인 권한(실제 API 인가에 쓰이는 EMPLOYEE/MANAGER/HR_ADMIN/SYSTEM_ADMIN)은 이 그룹코드를 기준으로 한다.
// 공통코드관리 탭의 기본 선택 그룹 및 안내 문구에만 쓰인다.
const ROLE_GROUP = 'USER_ROLE'
// 메뉴관리 탭은 로그인 권한이 아니라 권한레벨(조직상 직책: 사장/부문장/본부장/실장/팀장/파트장/직원 등)
// 기준으로 메뉴 표시 여부를 설정한다. 실제 로그인한 사용자의 users.level 값과 매칭된다.
const LEVEL_GROUP = 'LEVEL_ROLL'

// Layout.tsx의 menuKey와 맞춰서 관리한다. actions가 비어있으면 "표시" 칸만 토글 가능하다.
const MENU_ITEMS: { key: string; label: string; actions: string[] }[] = [
  { key: 'dashboard', label: '대시보드', actions: [] },
  { key: 'my-attendance', label: '출근부', actions: [] },
  { key: 'attendance', label: '근태 조회', actions: [] },
  { key: 'approvals', label: '승인함', actions: [] },
  { key: 'schedules', label: '일정관리', actions: [] },
  { key: 'employees', label: '직원 관리', actions: ['CREATE', 'BULK_CREATE', 'EDIT'] },
  { key: 'workplaces', label: '근무지 관리', actions: ['CREATE', 'EDIT'] },
  { key: 'organizations', label: '부서 관리', actions: ['CREATE', 'EDIT'] },
  { key: 'work-schedules', label: '근무제 관리', actions: ['CREATE', 'EDIT'] },
  { key: 'holidays', label: '휴일/휴가 관리', actions: ['CREATE', 'BULK_CREATE', 'EDIT'] },
  { key: 'audit-logs', label: '감사 로그', actions: [] },
  { key: 'permissions', label: '권한관리', actions: [] },
]

function CommonCodeTab() {
  const qc = useQueryClient()
  const [groupModal, setGroupModal] = useState<CommonCodeGroup | 'new' | null>(null)
  const [codeModal, setCodeModal] = useState<CommonCode | 'new' | null>(null)
  const [selectedGroup, setSelectedGroup] = useState<string>(LEVEL_GROUP)

  const { data: groups = [], isLoading: groupsLoading } = useQuery({
    queryKey: ['common-code-groups'],
    queryFn: () => getCommonCodeGroups().then(r => r.data.data),
  })
  const activeGroup = groups.some(g => g.groupCode === selectedGroup) ? selectedGroup : (groups[0]?.groupCode ?? selectedGroup)
  const activeGroupInfo = groups.find(g => g.groupCode === activeGroup)

  const { data: codes = [], isLoading: codesLoading } = useQuery({
    queryKey: ['common-codes', activeGroup],
    queryFn: () => getCommonCodes(activeGroup).then(r => r.data.data),
    enabled: !!activeGroup,
  })

  const delCode = useMutation({
    mutationFn: (id: number) => deleteCommonCode(id),
    onSuccess: () => { toast.success('삭제되었습니다.'); qc.invalidateQueries({ queryKey: ['common-codes'] }) },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '삭제 실패'),
  })

  return (
    <div>
      {/* 그룹코드 관리 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h2 style={{ fontSize: 16, fontWeight: 700, color: '#1e293b' }}>그룹코드 관리</h2>
        <button onClick={() => setGroupModal('new')} style={primaryBtnStyle}>+ 그룹 추가</button>
      </div>
      <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', overflow: 'hidden', marginBottom: 32 }}>
        {groupsLoading ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>로딩 중...</div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#f8fafc' }}>
                {['그룹코드', '그룹명', '설명', ''].map(h => <th key={h} style={thStyle}>{h}</th>)}
              </tr>
            </thead>
            <tbody>
              {groups.map(g => (
                <tr
                  key={g.id}
                  onClick={() => setSelectedGroup(g.groupCode)}
                  style={{ borderBottom: '1px solid #f1f5f9', cursor: 'pointer', background: g.groupCode === activeGroup ? '#eff6ff' : undefined }}
                >
                  <td style={tdStyle}>
                    {g.groupCode}
                    {g.protectedGroup && <span style={{ marginLeft: 6, fontSize: 10, color: '#94a3b8' }}>(기본)</span>}
                  </td>
                  <td style={tdStyle}>{g.groupName}</td>
                  <td style={tdStyle}>{g.description}</td>
                  <td style={{ ...tdStyle, textAlign: 'right', whiteSpace: 'nowrap' }}>
                    <button onClick={e => { e.stopPropagation(); setGroupModal(g) }} style={editBtnStyle}>수정</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* 하위 코드 관리 */}
      {activeGroup === ROLE_GROUP && (
        <div style={{ background: '#fef3c7', border: '1px solid #fde68a', borderRadius: 8, padding: '10px 14px', fontSize: 12, color: '#92400e', marginBottom: 16, lineHeight: 1.6 }}>
          ⚠️ 새 코드를 추가해도 실제 로그인 권한(백엔드 API 접근)은 자동으로 부여되지 않습니다. 기본 4개 역할(직원·팀장·인사담당자·시스템관리자)만
          시스템 인가 로직과 실제로 연결되어 있고, 새 코드는 메뉴관리 탭에서 화면 표시 설정만 가능합니다.
        </div>
      )}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, flexWrap: 'wrap', gap: 10 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <h2 style={{ fontSize: 16, fontWeight: 700, color: '#1e293b' }}>하위 코드 관리</h2>
          <select value={activeGroup} onChange={e => setSelectedGroup(e.target.value)} style={filterInputStyle}>
            {groups.map(g => <option key={g.groupCode} value={g.groupCode}>{g.groupName} ({g.groupCode})</option>)}
          </select>
        </div>
        <button onClick={() => setCodeModal('new')} style={primaryBtnStyle} disabled={!activeGroup}>+ 코드 추가</button>
      </div>
      <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', overflow: 'hidden' }}>
        {codesLoading ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>로딩 중...</div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#f8fafc' }}>
                {['코드', '명칭', '설명', '순서', '활성', ''].map(h => <th key={h} style={thStyle}>{h}</th>)}
              </tr>
            </thead>
            <tbody>
              {codes.map(c => (
                <tr key={c.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                  <td style={tdStyle}>
                    {c.code}
                    {c.protectedCode && <span style={{ marginLeft: 6, fontSize: 10, color: '#94a3b8' }}>(기본)</span>}
                  </td>
                  <td style={tdStyle}>{c.codeName}</td>
                  <td style={tdStyle}>{c.description}</td>
                  <td style={tdStyle}>{c.displayOrder}</td>
                  <td style={tdStyle}>{c.active ? '활성' : '비활성'}</td>
                  <td style={{ ...tdStyle, textAlign: 'right', whiteSpace: 'nowrap' }}>
                    <button onClick={() => setCodeModal(c)} style={editBtnStyle}>수정</button>{' '}
                    <button
                      onClick={() => { if (confirm('삭제하시겠습니까?')) delCode.mutate(c.id) }}
                      disabled={c.protectedCode}
                      title={c.protectedCode ? '기본 코드는 삭제할 수 없습니다.' : undefined}
                      style={{ ...deleteBtnStyle, opacity: c.protectedCode ? 0.4 : 1, cursor: c.protectedCode ? 'not-allowed' : 'pointer' }}
                    >삭제</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {groupModal && <CommonCodeGroupModal group={groupModal === 'new' ? undefined : groupModal} onClose={() => setGroupModal(null)} />}
      {codeModal && (
        <CommonCodeModal
          groupCode={activeGroup}
          groupLabel={activeGroupInfo ? `${activeGroupInfo.groupName} (${activeGroupInfo.groupCode})` : activeGroup}
          code={codeModal === 'new' ? undefined : codeModal}
          onClose={() => setCodeModal(null)}
        />
      )}
    </div>
  )
}

interface CommonCodeGroupFormData { groupCode: string; groupName: string; description: string }

function CommonCodeGroupModal({ group, onClose }: { group?: CommonCodeGroup; onClose: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState<CommonCodeGroupFormData>({
    groupCode: group?.groupCode ?? '', groupName: group?.groupName ?? '', description: group?.description ?? '',
  })
  const set = <K extends keyof CommonCodeGroupFormData>(k: K, v: CommonCodeGroupFormData[K]) => setForm(p => ({ ...p, [k]: v }))

  const mutation = useMutation({
    mutationFn: () => group
      ? updateCommonCodeGroup(group.id, { groupName: form.groupName, description: form.description })
      : createCommonCodeGroup({ groupCode: form.groupCode, groupName: form.groupName, description: form.description }),
    onSuccess: () => {
      toast.success(group ? '수정되었습니다.' : '등록되었습니다.')
      qc.invalidateQueries({ queryKey: ['common-code-groups'] })
      onClose()
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '오류가 발생했습니다.'),
  })

  return (
    <Overlay>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 400, boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, color: '#1e293b', marginBottom: 24 }}>{group ? '그룹코드 수정' : '그룹코드 추가'}</h2>
        <form onSubmit={e => { e.preventDefault(); mutation.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Field label="그룹코드">
            <input value={form.groupCode} onChange={e => set('groupCode', e.target.value)} required disabled={!!group} style={inputStyle} placeholder="예: LEAVE_TYPE" />
          </Field>
          <Field label="그룹명">
            <input value={form.groupName} onChange={e => set('groupName', e.target.value)} required style={inputStyle} />
          </Field>
          <Field label="설명">
            <input value={form.description} onChange={e => set('description', e.target.value)} style={inputStyle} />
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

interface CommonCodeFormData { code: string; codeName: string; description: string; displayOrder: number }

function CommonCodeModal({ groupCode, groupLabel, code, onClose }: { groupCode: string; groupLabel: string; code?: CommonCode; onClose: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState<CommonCodeFormData>({
    code: code?.code ?? '', codeName: code?.codeName ?? '',
    description: code?.description ?? '', displayOrder: code?.displayOrder ?? 0,
  })
  const set = <K extends keyof CommonCodeFormData>(k: K, v: CommonCodeFormData[K]) => setForm(p => ({ ...p, [k]: v }))

  const mutation = useMutation({
    mutationFn: () => code
      ? updateCommonCode(code.id, {
          codeName: form.codeName, description: form.description,
          displayOrder: form.displayOrder, active: code.active,
        })
      : createCommonCode({
          groupCode, code: form.code, codeName: form.codeName,
          description: form.description, displayOrder: form.displayOrder,
        }),
    onSuccess: () => {
      toast.success(code ? '수정되었습니다.' : '등록되었습니다.')
      qc.invalidateQueries({ queryKey: ['common-codes'] })
      onClose()
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '오류가 발생했습니다.'),
  })

  return (
    <Overlay>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 400, boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, color: '#1e293b', marginBottom: 4 }}>{code ? '코드 수정' : '코드 추가'}</h2>
        <p style={{ fontSize: 12, color: '#94a3b8', marginBottom: 20 }}>그룹: {groupLabel}</p>
        <form onSubmit={e => { e.preventDefault(); mutation.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Field label="코드">
            <input value={form.code} onChange={e => set('code', e.target.value)} required disabled={!!code} style={inputStyle} placeholder="예: ACCOUNTANT" />
          </Field>
          <Field label="명칭">
            <input value={form.codeName} onChange={e => set('codeName', e.target.value)} required style={inputStyle} />
          </Field>
          <Field label="설명">
            <input value={form.description} onChange={e => set('description', e.target.value)} style={inputStyle} />
          </Field>
          <Field label="순서">
            <input type="number" value={form.displayOrder} onChange={e => set('displayOrder', Number(e.target.value))} style={inputStyle} />
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

function MenuPermissionTab({ levels }: { levels: CommonCode[] }) {
  const qc = useQueryClient()
  const [level, setLevel] = useState<string>(levels[0]?.code ?? 'EMPLOYEE')
  const activeLevel = levels.some(l => l.code === level) ? level : (levels[0]?.code ?? level)

  const { data: overrides = [] } = useQuery({
    queryKey: ['menu-permissions', 'admin', activeLevel],
    queryFn: () => getMenuPermissionsByRole(activeLevel).then(r => r.data.data),
    enabled: !!activeLevel,
  })
  const overrideMap = new Map(overrides.map(o => [`${o.menuKey}:${o.actionKey}`, o.enabled]))
  const isEnabled = (menuKey: string, actionKey: string) => overrideMap.get(`${menuKey}:${actionKey}`) ?? true

  const toggle = useMutation({
    mutationFn: (vars: { menuKey: string; actionKey: string; enabled: boolean }) =>
      upsertMenuPermission({ role: activeLevel, menuKey: vars.menuKey, actionKey: vars.actionKey, enabled: vars.enabled }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['menu-permissions'] }),
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '변경 실패'),
  })

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <select value={activeLevel} onChange={e => setLevel(e.target.value)} style={filterInputStyle}>
          {levels.map(l => <option key={l.code} value={l.code}>{l.codeName} ({l.code})</option>)}
        </select>
      </div>
      <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', overflow: 'hidden' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ background: '#f8fafc' }}>
              {['메뉴', '표시', '등록', '일괄등록', '수정'].map(h => <th key={h} style={thStyle}>{h}</th>)}
            </tr>
          </thead>
          <tbody>
            {MENU_ITEMS.map(m => (
              <tr key={m.key} style={{ borderBottom: '1px solid #f1f5f9' }}>
                <td style={tdStyle}>{m.label}</td>
                <td style={tdStyle}>
                  <input
                    type="checkbox"
                    checked={isEnabled(m.key, 'MENU')}
                    onChange={e => toggle.mutate({ menuKey: m.key, actionKey: 'MENU', enabled: e.target.checked })}
                  />
                </td>
                {(['CREATE', 'BULK_CREATE', 'EDIT']).map(a => (
                  <td key={a} style={tdStyle}>
                    {m.actions.includes(a) ? (
                      <input
                        type="checkbox"
                        checked={isEnabled(m.key, a)}
                        onChange={e => toggle.mutate({ menuKey: m.key, actionKey: a, enabled: e.target.checked })}
                      />
                    ) : <span style={{ color: '#cbd5e1' }}>-</span>}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p style={{ fontSize: 12, color: '#94a3b8', marginTop: 12 }}>
        체크 해제 시 즉시 저장됩니다. 이 설정은 화면 표시/버튼 활성화만 제어하며, 서버 API 권한 자체는 바꾸지 않습니다.
      </p>
    </div>
  )
}

export default function PermissionsPage() {
  const [tab, setTab] = useState<'codes' | 'menus'>('codes')
  const { data: levels = [] } = useQuery({
    queryKey: ['common-codes', LEVEL_GROUP],
    queryFn: () => getCommonCodes(LEVEL_GROUP).then(r => r.data.data),
  })

  return (
    <div>
      <h1 style={{ fontSize: 22, fontWeight: 700, color: '#1e293b', marginBottom: 24 }}>권한관리</h1>
      <div style={{ display: 'flex', gap: 8, marginBottom: 20, borderBottom: '1px solid #e2e8f0' }}>
        {([{ key: 'codes', label: '공통코드관리' }, { key: 'menus', label: '메뉴관리' }] as const).map(t => (
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
      {tab === 'codes' ? <CommonCodeTab /> : <MenuPermissionTab levels={levels} />}
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
