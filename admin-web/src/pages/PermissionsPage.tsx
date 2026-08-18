import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getCommonCodes, createCommonCode, updateCommonCode, deleteCommonCode } from '@/api/commonCodes'
import type { CommonCode } from '@/api/commonCodes'
import { getCommonCodeGroups, createCommonCodeGroup, updateCommonCodeGroup } from '@/api/commonCodeGroups'
import type { CommonCodeGroup } from '@/api/commonCodeGroups'
import { getMenuPermissionsByRole, upsertMenuPermission } from '@/api/menuPermissions'
import { getCompanies, createCompany, updateCompany } from '@/api/companies'
import type { Company } from '@/api/companies'
import { getLogoUrl, uploadLogo } from '@/api/logo'
import type { LogoType } from '@/api/logo'
import { useLogoStore } from '@/store/logoStore'
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

// 회사 목록은 예외적으로 회사 경계를 넘어 전체가 보인다 — 새 회사를 만들려면 이미 어떤
// 회사들이 있는지 봐야 하기 때문이다(다른 업무 데이터는 여전히 완전히 격리되어 있음).
function CompanyManagementTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [editTarget, setEditTarget] = useState<Company | null>(null)

  const { data: companies = [], isLoading } = useQuery({
    queryKey: ['companies'],
    queryFn: () => getCompanies().then(r => r.data.data),
  })

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h2 style={{ fontSize: 16, fontWeight: 700, color: '#1e293b' }}>회사 관리</h2>
        <button onClick={() => setShowCreate(true)} style={primaryBtnStyle}>+ 회사 추가</button>
      </div>
      <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', overflow: 'hidden' }}>
        {isLoading ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>로딩 중...</div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#f8fafc' }}>
                {['회사명', '사업자번호', '주소', '전화번호', '상태', '등록일', ''].map(h => <th key={h} style={thStyle}>{h}</th>)}
              </tr>
            </thead>
            <tbody>
              {companies.map(c => (
                <tr key={c.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                  <td style={{ ...tdStyle, fontWeight: 500 }}>{c.name}</td>
                  <td style={tdStyle}>{c.businessNumber ?? '-'}</td>
                  <td style={tdStyle}>{c.address ?? '-'}</td>
                  <td style={tdStyle}>{c.phone ?? '-'}</td>
                  <td style={tdStyle}>{c.active ? '활성' : '비활성'}</td>
                  <td style={tdStyle}>{new Date(c.createdAt).toLocaleDateString('ko-KR')}</td>
                  <td style={{ ...tdStyle, textAlign: 'right', whiteSpace: 'nowrap' }}>
                    <button onClick={() => setEditTarget(c)} style={editBtnStyle}>수정</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {showCreate && <CompanyCreateModal onClose={() => { setShowCreate(false); qc.invalidateQueries({ queryKey: ['companies'] }) }} />}
      {editTarget && <CompanyEditModal company={editTarget} onClose={() => setEditTarget(null)} />}
    </div>
  )
}

interface CompanyCreateFormData {
  name: string; businessNumber: string; address: string; phone: string
  adminEmail: string; adminName: string; adminEmployeeNumber: string
}

function CompanyCreateModal({ onClose }: { onClose: () => void }) {
  const [form, setForm] = useState<CompanyCreateFormData>({
    name: '', businessNumber: '', address: '', phone: '',
    adminEmail: '', adminName: '', adminEmployeeNumber: '',
  })
  const set = <K extends keyof CompanyCreateFormData>(k: K, v: CompanyCreateFormData[K]) => setForm(p => ({ ...p, [k]: v }))

  const mutation = useMutation({
    mutationFn: () => createCompany({
      name: form.name,
      businessNumber: form.businessNumber || undefined,
      address: form.address || undefined,
      phone: form.phone || undefined,
      adminEmail: form.adminEmail,
      adminName: form.adminName,
      adminEmployeeNumber: form.adminEmployeeNumber || undefined,
    }),
    onSuccess: (res) => {
      const { temporaryPassword, adminEmail } = res.data.data
      window.alert(
        `회사가 등록되었습니다.\n\n관리자 이메일: ${adminEmail}\n임시 비밀번호: ${temporaryPassword}\n\n` +
        '해당 회사 관리자에게 안전한 방법으로 전달한 뒤 최초 로그인 시 비밀번호 변경을 안내해주세요.\n' +
        '기본 조직("본사")·근무지("본사(임시)")·근무제("기본 근무제")는 임시값이니 로그인 후 화면에서 실제 정보로 수정해주세요.'
      )
      onClose()
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '등록 실패'),
  })

  return (
    <Overlay>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 440, maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, color: '#1e293b', marginBottom: 24 }}>회사 추가</h2>
        <form onSubmit={e => { e.preventDefault(); mutation.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <p style={{ fontSize: 13, fontWeight: 600, color: '#374151', margin: 0 }}>회사 정보</p>
          <Field label="회사명">
            <input value={form.name} onChange={e => set('name', e.target.value)} required style={inputStyle} />
          </Field>
          <Field label="사업자번호">
            <input value={form.businessNumber} onChange={e => set('businessNumber', e.target.value)} style={inputStyle} />
          </Field>
          <Field label="주소">
            <input value={form.address} onChange={e => set('address', e.target.value)} style={inputStyle} />
          </Field>
          <Field label="전화번호">
            <input value={form.phone} onChange={e => set('phone', e.target.value)} style={inputStyle} />
          </Field>
          <p style={{ fontSize: 13, fontWeight: 600, color: '#374151', margin: '10px 0 0' }}>최초 관리자 계정</p>
          <Field label="이메일">
            <input type="email" value={form.adminEmail} onChange={e => set('adminEmail', e.target.value)} required style={inputStyle} />
          </Field>
          <Field label="이름">
            <input value={form.adminName} onChange={e => set('adminName', e.target.value)} required style={inputStyle} />
          </Field>
          <Field label="사번">
            <input value={form.adminEmployeeNumber} onChange={e => set('adminEmployeeNumber', e.target.value)} style={inputStyle} placeholder="SYS001 (미입력 시 자동)" />
          </Field>
          <p style={{ fontSize: 12, color: '#94a3b8', margin: 0 }}>
            임시 비밀번호가 자동 생성되어 등록 완료 후 한 번만 화면에 표시됩니다. 최초 로그인 시 비밀번호 변경이 강제됩니다.
          </p>
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

interface CompanyEditFormData { name: string; businessNumber: string; address: string; phone: string }

function CompanyEditModal({ company, onClose }: { company: Company; onClose: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState<CompanyEditFormData>({
    name: company.name, businessNumber: company.businessNumber ?? '',
    address: company.address ?? '', phone: company.phone ?? '',
  })
  const set = <K extends keyof CompanyEditFormData>(k: K, v: CompanyEditFormData[K]) => setForm(p => ({ ...p, [k]: v }))

  const mutation = useMutation({
    mutationFn: () => updateCompany(company.id, {
      name: form.name,
      businessNumber: form.businessNumber || undefined,
      address: form.address || undefined,
      phone: form.phone || undefined,
    }),
    onSuccess: () => {
      toast.success('수정되었습니다.')
      qc.invalidateQueries({ queryKey: ['companies'] })
      onClose()
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '수정 실패'),
  })

  return (
    <Overlay>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 400, boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, color: '#1e293b', marginBottom: 24 }}>회사 정보 수정</h2>
        <form onSubmit={e => { e.preventDefault(); mutation.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Field label="회사명">
            <input value={form.name} onChange={e => set('name', e.target.value)} required style={inputStyle} />
          </Field>
          <Field label="사업자번호">
            <input value={form.businessNumber} onChange={e => set('businessNumber', e.target.value)} style={inputStyle} />
          </Field>
          <Field label="주소">
            <input value={form.address} onChange={e => set('address', e.target.value)} style={inputStyle} />
          </Field>
          <Field label="전화번호">
            <input value={form.phone} onChange={e => set('phone', e.target.value)} style={inputStyle} />
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

// 로그인 화면(관리자웹 로그인 페이지 + 모바일 앱 로그인 화면)용과 관리자웹 좌측 메뉴 하단용을
// 서로 다른 이미지로 독립 관리한다 — 각 위치가 서로 다른 크기/배경을 갖기 때문이다(사용자 확인 완료).
// 회사 구분은 없다 — 로그인 화면은 인증 전이라 어느 회사 소속인지 알 수 없으므로 회사별 로고는
// 애초에 불가능하다.
function LogoManagementTab() {
  return (
    <div>
      <h2 style={{ fontSize: 16, fontWeight: 700, color: '#1e293b', marginBottom: 4 }}>로고 관리</h2>
      <p style={{ fontSize: 13, color: '#94a3b8', marginBottom: 20 }}>
        로그인 화면용 로고와 좌측 메뉴 하단용 로고를 각각 따로 업로드할 수 있습니다.
      </p>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        <LogoUploadCard
          type="login"
          title="로그인 화면 로고"
          description="관리자웹 로그인 화면과 모바일 앱 로그인 화면(좌측 상단)에 표시됩니다."
        />
        <LogoUploadCard
          type="sidebar"
          title="좌측 메뉴 하단 로고"
          description="관리자웹 로그인 후 좌측 메뉴 하단에 표시됩니다."
        />
      </div>
    </div>
  )
}

function LogoUploadCard({ type, title, description }: { type: LogoType; title: string; description: string }) {
  const version = useLogoStore((s) => s.version[type])
  const bump = useLogoStore((s) => s.bump)
  const [file, setFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  const handleSelect = (f: File | null) => {
    setFile(f)
    setPreviewUrl(f ? URL.createObjectURL(f) : null)
  }

  const handleUpload = async () => {
    if (!file) { toast.error('이미지 파일을 선택해주세요.'); return }
    setUploading(true)
    try {
      await uploadLogo(type, file)
      bump(type)
      toast.success('로고가 적용되었습니다.')
      setFile(null)
      setPreviewUrl(null)
    } catch (err: any) {
      toast.error(err?.response?.data?.message ?? '업로드 실패')
    } finally {
      setUploading(false)
    }
  }

  return (
    <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', padding: 24 }}>
      <p style={{ fontSize: 14, fontWeight: 700, color: '#1e293b', marginBottom: 4 }}>{title}</p>
      <p style={{ fontSize: 12, color: '#94a3b8', marginBottom: 16 }}>{description}</p>

      <p style={{ fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 10 }}>현재 로고</p>
      <div style={{
        width: 200, height: 100, border: '1px solid #e2e8f0', borderRadius: 8,
        display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 24, background: '#f8fafc',
      }}>
        <img
          src={getLogoUrl(type, version)}
          alt="현재 로고"
          style={{ maxWidth: '90%', maxHeight: '90%' }}
          onError={e => { e.currentTarget.style.display = 'none' }}
        />
      </div>

      <p style={{ fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 10 }}>새 로고 업로드</p>
      <input
        type="file"
        accept="image/png,image/jpeg"
        onChange={e => handleSelect(e.target.files?.[0] ?? null)}
        style={{ fontSize: 13, marginBottom: 14 }}
      />
      {previewUrl && (
        <div style={{
          width: 200, height: 100, border: '1px solid #bfdbfe', borderRadius: 8,
          display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 14, background: '#eff6ff',
        }}>
          <img src={previewUrl} alt="선택한 이미지 미리보기" style={{ maxWidth: '90%', maxHeight: '90%' }} />
        </div>
      )}
      <p style={{ fontSize: 12, color: '#94a3b8', marginBottom: 14 }}>PNG 또는 JPEG, 최대 2MB.</p>
      <button onClick={handleUpload} disabled={!file || uploading} style={{ ...primaryBtnStyle, opacity: !file || uploading ? 0.5 : 1 }}>
        {uploading ? '업로드 중...' : '업로드'}
      </button>
    </div>
  )
}

export default function PermissionsPage() {
  const [tab, setTab] = useState<'codes' | 'menus' | 'companies' | 'logo'>('codes')
  const { data: levels = [] } = useQuery({
    queryKey: ['common-codes', LEVEL_GROUP],
    queryFn: () => getCommonCodes(LEVEL_GROUP).then(r => r.data.data),
  })

  return (
    <div>
      <h1 style={{ fontSize: 22, fontWeight: 700, color: '#1e293b', marginBottom: 24 }}>권한관리</h1>
      <div style={{ display: 'flex', gap: 8, marginBottom: 20, borderBottom: '1px solid #e2e8f0' }}>
        {([{ key: 'codes', label: '공통코드관리' }, { key: 'menus', label: '메뉴관리' }, { key: 'companies', label: '회사관리' }, { key: 'logo', label: '로고관리' }] as const).map(t => (
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
      {tab === 'codes' ? <CommonCodeTab />
        : tab === 'menus' ? <MenuPermissionTab levels={levels} />
        : tab === 'companies' ? <CompanyManagementTab />
        : <LogoManagementTab />}
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
