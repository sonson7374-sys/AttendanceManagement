import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getOrganizations, createOrganization, updateOrganization, deleteOrganization } from '@/api/organizations'
import type { Organization } from '@/types'
import { usePermissions } from '@/hooks/usePermissions'
import toast from 'react-hot-toast'

function OrgModal({ org, orgs, onClose }: { org?: Organization; orgs: Organization[]; onClose: () => void }) {
  const qc = useQueryClient()
  const [name, setName] = useState(org?.name ?? '')
  const [parentId, setParentId] = useState<string>(org?.parentId?.toString() ?? '')
  const [order, setOrder] = useState(org?.displayOrder?.toString() ?? '')

  const mutation = useMutation({
    mutationFn: () =>
      org
        ? updateOrganization(org.id, { name, parentId: parentId ? Number(parentId) : undefined, displayOrder: order ? Number(order) : undefined })
        : createOrganization({ name, parentId: parentId ? Number(parentId) : undefined, displayOrder: order ? Number(order) : undefined }),
    onSuccess: () => {
      toast.success(org ? '수정되었습니다.' : '생성되었습니다.')
      qc.invalidateQueries({ queryKey: ['organizations'] })
      onClose()
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '오류 발생'),
  })

  return (
    <Overlay>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 420, boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, color: '#1e293b', marginBottom: 24 }}>{org ? '부서 수정' : '부서 등록'}</h2>
        <form onSubmit={e => { e.preventDefault(); mutation.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={labelStyle}>부서명 *</label>
            <input value={name} onChange={e => setName(e.target.value)} required style={inputStyle} placeholder="예: 개발팀" />
          </div>
          <div>
            <label style={labelStyle}>상위 부서</label>
            <select value={parentId} onChange={e => setParentId(e.target.value)} style={inputStyle}>
              <option value="">최상위</option>
              {orgs.filter(o => o.id !== org?.id).map(o => (
                <option key={o.id} value={o.id}>{o.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label style={labelStyle}>정렬 순서</label>
            <input type="number" value={order} onChange={e => setOrder(e.target.value)} style={inputStyle} placeholder="0" />
          </div>
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

export default function OrganizationsPage() {
  const qc = useQueryClient()
  const [modalOrg, setModalOrg] = useState<Organization | null | 'new'>(null)
  const { isActionEnabled } = usePermissions()
  const canCreate = isActionEnabled('organizations', 'CREATE')

  const { data: orgs = [], isLoading } = useQuery({
    queryKey: ['organizations'],
    queryFn: () => getOrganizations().then(r => r.data.data),
  })

  const del = useMutation({
    mutationFn: (id: number) => deleteOrganization(id),
    onSuccess: () => { toast.success('삭제되었습니다.'); qc.invalidateQueries({ queryKey: ['organizations'] }) },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '삭제 실패'),
  })

  const rootOrgs = orgs.filter((o: Organization) => !o.parentId)
  const children = (parentId: number) => orgs.filter((o: Organization) => o.parentId === parentId)

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, color: '#1e293b' }}>부서 관리</h1>
        {canCreate && <button onClick={() => setModalOrg('new')} style={primaryBtnStyle}>+ 부서 등록</button>}
      </div>

      {isLoading ? <p style={{ color: '#64748b' }}>로딩 중...</p> : (
        <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', overflow: 'hidden' }}>
          {orgs.length === 0 ? (
            <div style={{ padding: 48, textAlign: 'center', color: '#64748b' }}>등록된 부서가 없습니다.</div>
          ) : (
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ background: '#f8fafc' }}>
                  {['부서명', '상위 부서', '정렬', ''].map(h => <th key={h} style={thStyle}>{h}</th>)}
                </tr>
              </thead>
              <tbody>
                {rootOrgs.map((org: Organization) => (
                  <OrgRow
                    key={org.id}
                    org={org}
                    orgs={orgs}
                    depth={0}
                    childrenOf={children}
                    onEdit={setModalOrg}
                    onDelete={(id) => del.mutate(id)}
                  />
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {modalOrg === 'new' && <OrgModal orgs={orgs} onClose={() => setModalOrg(null)} />}
      {modalOrg && modalOrg !== 'new' && <OrgModal org={modalOrg as Organization} orgs={orgs} onClose={() => setModalOrg(null)} />}
    </div>
  )
}

function OrgRow({ org, orgs, depth, childrenOf, onEdit, onDelete }: {
  org: Organization; orgs: Organization[]; depth: number
  childrenOf: (id: number) => Organization[]
  onEdit: (org: Organization) => void
  onDelete: (id: number) => void
}) {
  const ch = childrenOf(org.id)
  const { isActionEnabled } = usePermissions()
  return (
    <>
      <tr style={{ borderBottom: '1px solid #f1f5f9' }}>
        <td style={{ ...tdStyle, paddingLeft: 16 + depth * 24 }}>
          <span style={{ fontSize: 13 }}>{depth > 0 ? '└ ' : ''}</span>
          <span style={{ fontWeight: depth === 0 ? 600 : 400 }}>{org.name}</span>
        </td>
        <td style={tdStyle}>{org.parentId ? orgs.find(o => o.id === org.parentId)?.name ?? '-' : '-'}</td>
        <td style={tdStyle}>{org.displayOrder ?? '-'}</td>
        <td style={{ ...tdStyle, textAlign: 'right' }}>
          {isActionEnabled('organizations', 'EDIT') && <button onClick={() => onEdit(org)} style={editBtnStyle}>수정</button>}
          <button onClick={() => onDelete(org.id)} style={deleteBtnStyle}>삭제</button>
        </td>
      </tr>
      {ch.map((c: Organization) => (
        <OrgRow
          key={c.id}
          org={c}
          orgs={orgs}
          depth={depth + 1}
          childrenOf={childrenOf}
          onEdit={onEdit}
          onDelete={onDelete}
        />
      ))}
    </>
  )
}

function Overlay({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100 }}>
      {children}
    </div>
  )
}

const labelStyle: React.CSSProperties = { display: 'block', fontSize: 13, fontWeight: 500, color: '#374151', marginBottom: 4 }
const inputStyle: React.CSSProperties = { width: '100%', padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' }
const thStyle: React.CSSProperties = { padding: '12px 16px', textAlign: 'left', fontSize: 12, fontWeight: 600, color: '#64748b', borderBottom: '1px solid #e2e8f0' }
const tdStyle: React.CSSProperties = { padding: '12px 16px', fontSize: 14, color: '#374151' }
const primaryBtnStyle: React.CSSProperties = { padding: '8px 18px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontWeight: 600, fontSize: 14 }
const cancelBtnStyle: React.CSSProperties = { flex: 1, padding: 10, border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontSize: 14, background: '#fff', color: '#374151' }
const editBtnStyle: React.CSSProperties = { marginRight: 8, padding: '4px 12px', fontSize: 12, color: '#2563eb', border: '1px solid #bfdbfe', borderRadius: 6, background: '#fff', cursor: 'pointer' }
const deleteBtnStyle: React.CSSProperties = { padding: '4px 12px', fontSize: 12, color: '#ef4444', border: '1px solid #fca5a5', borderRadius: 6, background: '#fff', cursor: 'pointer' }
