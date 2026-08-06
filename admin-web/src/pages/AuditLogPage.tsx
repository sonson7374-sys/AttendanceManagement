import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getAuditLogs } from '@/api/admin'
import type { AuditLog } from '@/types'

const fmtDate = (iso: string) => new Date(iso).toLocaleString('ko-KR')

export default function AuditLogPage() {
  const [email, setEmail] = useState('')
  const [search, setSearch] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['audit-logs', search],
    queryFn: () => getAuditLogs(search ? { actorEmail: search } : {}).then(r => r.data.data),
  })

  const logs = data?.content ?? []

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, color: '#1e293b' }}>감사 로그</h1>
        <div style={{ display: 'flex', gap: 8 }}>
          <input
            value={email}
            onChange={e => setEmail(e.target.value)}
            placeholder="이메일로 검색..."
            style={{ padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14, width: 220 }}
            onKeyDown={e => e.key === 'Enter' && setSearch(email)}
          />
          <button onClick={() => setSearch(email)} style={btnStyle}>검색</button>
          {search && <button onClick={() => { setSearch(''); setEmail('') }} style={cancelBtnStyle}>초기화</button>}
        </div>
      </div>

      <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', overflow: 'hidden' }}>
        {isLoading ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>로딩 중...</div>
        ) : logs.length === 0 ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>감사 로그가 없습니다.</div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#f8fafc' }}>
                {['시각', '행위자', '액션', '대상 유형', '대상 ID', '상세'].map(h => (
                  <th key={h} style={thStyle}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {logs.map((log: AuditLog) => (
                <tr key={log.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                  <td style={{ ...tdStyle, whiteSpace: 'nowrap', fontSize: 12 }}>{fmtDate(log.createdAt)}</td>
                  <td style={tdStyle}>{log.actorEmail ?? '-'}</td>
                  <td style={tdStyle}>
                    <span style={{
                      padding: '2px 8px', borderRadius: 4, fontSize: 12, fontWeight: 600,
                      background: actionColor(log.action) + '20', color: actionColor(log.action)
                    }}>{log.action}</span>
                  </td>
                  <td style={tdStyle}>{log.targetType ?? '-'}</td>
                  <td style={tdStyle}>{log.targetId ?? '-'}</td>
                  <td style={{ ...tdStyle, maxWidth: 320 }}>
                    {log.detail ? (
                      <details>
                        <summary style={{ cursor: 'pointer', fontSize: 12, color: '#64748b' }}>보기</summary>
                        <pre style={{ fontSize: 11, color: '#374151', marginTop: 6, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                          {JSON.stringify(log.detail, null, 2)}
                        </pre>
                      </details>
                    ) : '-'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {data && <p style={{ marginTop: 10, fontSize: 12, color: '#94a3b8' }}>총 {data.totalElements}건</p>}
    </div>
  )
}

function actionColor(action: string) {
  if (action.includes('APPROVED')) return '#10b981'
  if (action.includes('REJECTED')) return '#ef4444'
  if (action.includes('CHANGE') || action.includes('UPDATE')) return '#f97316'
  if (action.includes('CREATE')) return '#2563eb'
  if (action.includes('DELETE') || action.includes('LOCK')) return '#ef4444'
  return '#64748b'
}

const thStyle: React.CSSProperties = { padding: '12px 16px', textAlign: 'left', fontSize: 12, fontWeight: 600, color: '#64748b', borderBottom: '1px solid #e2e8f0' }
const tdStyle: React.CSSProperties = { padding: '12px 16px', fontSize: 14, color: '#374151' }
const btnStyle: React.CSSProperties = { padding: '8px 16px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 14, fontWeight: 600 }
const cancelBtnStyle: React.CSSProperties = { padding: '8px 16px', background: '#fff', color: '#64748b', border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontSize: 14 }
