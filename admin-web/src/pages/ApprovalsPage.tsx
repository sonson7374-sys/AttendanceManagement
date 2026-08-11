import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getPendingChangeRequests, processChangeRequest, getChangeRequestHistory, submitChangeRequest, getAttendanceScopeInfo } from '@/api/admin'
import { getPendingLeaveRequests, processLeaveRequest, getLeaveRequestHistory, submitLeaveRequest } from '@/api/leaveRequests'
import { getPendingOutsideWorkRequests, processOutsideWorkRequest, getOutsideWorkRequestHistory, submitOutsideWorkRequest } from '@/api/outsideWorkRequests'
import { getPendingWorkplaceChangeRequests, processWorkplaceChangeRequest, getWorkplaceChangeRequestHistory } from '@/api/workplaceChangeRequests'
import { getPendingWorkScheduleChangeRequests, processWorkScheduleChangeRequest, getWorkScheduleChangeRequestHistory } from '@/api/workScheduleChangeRequests'
import { getMyAttendanceHistory, getMyAssignedWorkplaces } from '@/api/myAttendance'
import type {
  ApprovalQueueItem, ChangeRequestStatus, ChangeRequestType, LeaveRequestType, OutsideWorkRequestType, AttendanceStatus,
} from '@/types'
import { useAuthStore } from '@/store/authStore'
import toast from 'react-hot-toast'

// 승인/반려 버튼은 권한레벨(그룹코드 LEVEL_ROLL)이 이 값에 속한 계정에게만 노출된다.
// 서버(ChangeRequestService/LeaveRequestService/OutsideWorkRequestService.process())도 동일한 기준으로 검증하므로
// 이 배열은 화면 표시용일 뿐이며, 실제 권한 통제는 서버에서 이루어진다.
const APPROVER_LEVELS = ['SYSADMIN', 'HRADMIN', 'PRESIDENT']

// 파트장 이상 레벨은 근태 수정 요청 중 이 유형들만(본인 조직 산하에 한해) 직접 승인/반려할 수 있다.
// 서버(ChangeRequestService.process())도 동일한 기준으로 검증하므로 이 목록은 화면 표시용일 뿐이다.
const PART_LEAD_APPROVABLE_CHANGE_TYPES: ChangeRequestType[] = ['CHECK_IN_TIME', 'CHECK_OUT_TIME', 'ABSENT_CORRECTION']

const CHANGE_TYPE_LABEL: Record<ChangeRequestType, string> = {
  CHECK_IN_TIME: '출근 시간 수정',
  CHECK_OUT_TIME: '퇴근 시간 수정',
  ABSENT_CORRECTION: '결근 처리 수정',
  WORKPLACE_CHANGE: '근무지 변경',
  LATE_CORRECTION: '지각 정정',
}

const LEAVE_TYPE_LABEL: Record<LeaveRequestType, string> = {
  ANNUAL: '연차', HALF_DAY: '반차', HOURLY: '반반차', SICK: '병가',
  OFFICIAL: '공가', OVERTIME: '연장근무', HOLIDAY_WORK: '휴일근무',
  ZERO_DAY: '대체휴가', EARLY: '조기퇴근',
}

const OUTSIDE_WORK_TYPE_LABEL: Record<OutsideWorkRequestType, string> = {
  OUTSIDE_WORK: '외근', BUSINESS_TRIP: '출장', REMOTE_WORK: '재택근무',
}

// 신청 탭: 모바일 앱과 동일하게 휴가·외근·출장·재택을 하나의 신청 유형 목록으로 합쳐서 보여주고,
// 제출 시 유형에 따라 /leave-requests 또는 /outside-work-requests로 나눠 보낸다.
const OUTSIDE_WORK_TYPE_SET = new Set<string>(Object.keys(OUTSIDE_WORK_TYPE_LABEL))
const LEAVE_OR_OUTSIDE_WORK_OPTIONS: { value: LeaveRequestType | OutsideWorkRequestType; label: string }[] = [
  ...(Object.entries(LEAVE_TYPE_LABEL) as [LeaveRequestType, string][]).map(([value, label]) => ({ value, label })),
  ...(Object.entries(OUTSIDE_WORK_TYPE_LABEL) as [OutsideWorkRequestType, string][]).map(([value, label]) => ({ value, label })),
]

const RECORD_STATUS_LABEL: Partial<Record<AttendanceStatus, string>> = {
  BEFORE_WORK: '미출근', WORKING: '근무 중', BREAK: '휴게', FINISHED: '퇴근',
  LATE: '지각', EARLY_LEAVE: '조퇴', ABSENT: '결근', LEAVE: '휴가',
  OUTSIDE_WORK: '외근', BUSINESS_TRIP: '출장', REMOTE_WORK: '재택',
}
const weekdayLabel = (dateStr: string) => ['일', '월', '화', '수', '목', '금', '토'][new Date(`${dateStr}T00:00:00`).getDay()]
const toIsoOrUndefined = (v: string) => v ? new Date(v).toISOString() : undefined

const KIND_LABEL: Record<ApprovalQueueItem['kind'], string> = {
  CHANGE_REQUEST: '근태 수정',
  LEAVE_REQUEST: '휴가',
  OUTSIDE_WORK_REQUEST: '외근·출장',
  WORKPLACE_CHANGE_REQUEST: '근무지 변경요청',
  WORK_SCHEDULE_CHANGE_REQUEST: '근무제 변경요청',
}

const KIND_COLOR: Record<ApprovalQueueItem['kind'], string> = {
  CHANGE_REQUEST: '#8b5cf6',
  LEAVE_REQUEST: '#0ea5e9',
  OUTSIDE_WORK_REQUEST: '#14b8a6',
  WORKPLACE_CHANGE_REQUEST: '#f97316',
  WORK_SCHEDULE_CHANGE_REQUEST: '#a855f7',
}

const STATUS_LABEL: Record<ChangeRequestStatus, string> = {
  PENDING: '대기', APPROVED: '승인', REJECTED: '반려', CANCELED: '취소',
}

const STATUS_COLOR: Record<ChangeRequestStatus, string> = {
  PENDING: '#f59e0b', APPROVED: '#10b981', REJECTED: '#ef4444', CANCELED: '#94a3b8',
}

function formatDate(isoString: string) {
  return new Date(isoString).toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

function requesterLabel(item: ApprovalQueueItem): string {
  const name = item.changeRequest?.requesterName ?? item.leaveRequest?.requesterName ?? item.outsideWorkRequest?.requesterName ?? item.workplaceChangeRequest?.requesterName ?? item.workScheduleChangeRequest?.requesterName
  const id = item.changeRequest?.requesterId ?? item.leaveRequest?.requesterId ?? item.outsideWorkRequest?.requesterId ?? item.workplaceChangeRequest?.requesterId ?? item.workScheduleChangeRequest?.requesterId
  return name ?? `#${id}`
}

function detailText(item: ApprovalQueueItem): string {
  if (item.kind === 'CHANGE_REQUEST') return CHANGE_TYPE_LABEL[item.changeRequest!.changeType]
  if (item.kind === 'LEAVE_REQUEST') return LEAVE_TYPE_LABEL[item.leaveRequest!.requestType]
  if (item.kind === 'OUTSIDE_WORK_REQUEST') return OUTSIDE_WORK_TYPE_LABEL[item.outsideWorkRequest!.requestType]
  return '-'
}

function summaryText(item: ApprovalQueueItem): string {
  if (item.kind === 'CHANGE_REQUEST') return `대상일 ${item.changeRequest!.targetDate}`
  if (item.kind === 'LEAVE_REQUEST') return `${formatDate(item.leaveRequest!.startAt)} ~ ${formatDate(item.leaveRequest!.endAt)}`
  if (item.kind === 'WORKPLACE_CHANGE_REQUEST') return `${item.workplaceChangeRequest!.name} · 적용일 ${item.workplaceChangeRequest!.effectiveDate}`
  if (item.kind === 'WORK_SCHEDULE_CHANGE_REQUEST') return `${item.workScheduleChangeRequest!.targetWorkScheduleName ?? '#' + item.workScheduleChangeRequest!.targetWorkScheduleId} · 적용월 ${item.workScheduleChangeRequest!.effectiveMonth}`
  return `${formatDate(item.outsideWorkRequest!.startAt)} ~ ${formatDate(item.outsideWorkRequest!.endAt)}`
}

function reasonText(item: ApprovalQueueItem): string {
  return item.changeRequest?.reason ?? item.leaveRequest?.reason ?? item.outsideWorkRequest?.reason ?? item.workplaceChangeRequest?.reason ?? item.workScheduleChangeRequest?.reason ?? ''
}

type HistoryKind = 'ALL' | ApprovalQueueItem['kind']

// 직원부터 부문장까지 누구나 신청 가능(모바일 앱과 동일). 대기중 탭이 본인 신청 건도 함께 보여주므로
// 여기서는 신청 폼만 두고, 목록/상태 확인은 대기중·요청이력 탭에서 하도록 한다.
function SubmitTab() {
  const [kind, setKind] = useState<'CHANGE' | 'LEAVE'>('CHANGE')
  return (
    <div>
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <button onClick={() => setKind('CHANGE')} style={kind === 'CHANGE' ? subTabActiveStyle : subTabStyle}>근태 수정 요청</button>
        <button onClick={() => setKind('LEAVE')} style={kind === 'LEAVE' ? subTabActiveStyle : subTabStyle}>휴가·외근·출장·재택</button>
      </div>
      {kind === 'CHANGE' ? <ChangeRequestForm /> : <LeaveOutsideWorkForm />}
    </div>
  )
}

function LeaveOutsideWorkForm() {
  const queryClient = useQueryClient()
  const [requestType, setRequestType] = useState<LeaveRequestType | OutsideWorkRequestType>('ANNUAL')
  const [startAt, setStartAt] = useState('')
  const [endAt, setEndAt] = useState('')
  const [reason, setReason] = useState('')
  const [destinationAddress, setDestinationAddress] = useState('')
  const [destinationLatitude, setDestinationLatitude] = useState('')
  const [destinationLongitude, setDestinationLongitude] = useState('')
  const [tempRadiusMeters, setTempRadiusMeters] = useState('')
  const [visitPurpose, setVisitPurpose] = useState('')
  const [clientName, setClientName] = useState('')
  const [expectedReturnAt, setExpectedReturnAt] = useState('')

  const isOutsideWork = OUTSIDE_WORK_TYPE_SET.has(requestType)

  const reset = () => {
    setReason(''); setDestinationAddress(''); setDestinationLatitude(''); setDestinationLongitude('')
    setTempRadiusMeters(''); setVisitPurpose(''); setClientName(''); setExpectedReturnAt('')
    setStartAt(''); setEndAt('')
  }

  const mutation = useMutation({
    mutationFn: (): Promise<unknown> => {
      if (!startAt || !endAt) throw new Error('기간을 입력해주세요.')
      if (!reason.trim()) throw new Error('사유를 입력해주세요.')
      const startIso = new Date(startAt).toISOString()
      const endIso = new Date(endAt).toISOString()
      if (isOutsideWork) {
        return submitOutsideWorkRequest({
          requestType: requestType as OutsideWorkRequestType,
          startAt: startIso, endAt: endIso, reason,
          destinationAddress: destinationAddress || undefined,
          destinationLatitude: destinationLatitude ? Number(destinationLatitude) : undefined,
          destinationLongitude: destinationLongitude ? Number(destinationLongitude) : undefined,
          tempRadiusMeters: tempRadiusMeters ? Number(tempRadiusMeters) : undefined,
          visitPurpose: visitPurpose || undefined,
          clientName: clientName || undefined,
          expectedReturnAt: toIsoOrUndefined(expectedReturnAt),
        })
      }
      return submitLeaveRequest({ requestType: requestType as LeaveRequestType, startAt: startIso, endAt: endIso, reason })
    },
    onSuccess: () => {
      toast.success('신청되었습니다.')
      reset()
      queryClient.invalidateQueries({ queryKey: ['pending-leave-requests'] })
      queryClient.invalidateQueries({ queryKey: ['pending-outside-work-requests'] })
      queryClient.invalidateQueries({ queryKey: ['request-history'] })
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? e?.message ?? '신청 실패'),
  })

  return (
    <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', padding: 24, maxWidth: 520 }}>
      <form onSubmit={e => { e.preventDefault(); mutation.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div>
          <label style={formLabelStyle}>신청 유형</label>
          <select value={requestType} onChange={e => setRequestType(e.target.value as LeaveRequestType | OutsideWorkRequestType)} style={formInputStyle}>
            {LEAVE_OR_OUTSIDE_WORK_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
          <div>
            <label style={formLabelStyle}>시작</label>
            <input type="datetime-local" value={startAt} onChange={e => setStartAt(e.target.value)} required style={formInputStyle} />
          </div>
          <div>
            <label style={formLabelStyle}>종료</label>
            <input type="datetime-local" value={endAt} onChange={e => setEndAt(e.target.value)} required style={formInputStyle} />
          </div>
        </div>

        {isOutsideWork && (
          <>
            <div>
              <label style={formLabelStyle}>목적지 주소</label>
              <input value={destinationAddress} onChange={e => setDestinationAddress(e.target.value)} style={formInputStyle} />
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10 }}>
              <div>
                <label style={formLabelStyle}>위도</label>
                <input value={destinationLatitude} onChange={e => setDestinationLatitude(e.target.value)} style={formInputStyle} placeholder="37.5" />
              </div>
              <div>
                <label style={formLabelStyle}>경도</label>
                <input value={destinationLongitude} onChange={e => setDestinationLongitude(e.target.value)} style={formInputStyle} placeholder="127.0" />
              </div>
              <div>
                <label style={formLabelStyle}>임시 허용 반경(m)</label>
                <input value={tempRadiusMeters} onChange={e => setTempRadiusMeters(e.target.value)} style={formInputStyle} />
              </div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <div>
                <label style={formLabelStyle}>방문 목적</label>
                <input value={visitPurpose} onChange={e => setVisitPurpose(e.target.value)} style={formInputStyle} />
              </div>
              <div>
                <label style={formLabelStyle}>고객사명</label>
                <input value={clientName} onChange={e => setClientName(e.target.value)} style={formInputStyle} />
              </div>
            </div>
            <div>
              <label style={formLabelStyle}>예정 복귀시간</label>
              <input type="datetime-local" value={expectedReturnAt} onChange={e => setExpectedReturnAt(e.target.value)} style={formInputStyle} />
            </div>
          </>
        )}

        <div>
          <label style={formLabelStyle}>사유</label>
          <textarea value={reason} onChange={e => setReason(e.target.value)} required rows={3} style={{ ...formInputStyle, resize: 'vertical' }} />
        </div>

        <button type="submit" disabled={mutation.isPending} style={{ ...primarySubmitBtnStyle, opacity: mutation.isPending ? 0.6 : 1 }}>
          {mutation.isPending ? '신청 중...' : '신청'}
        </button>
      </form>
    </div>
  )
}

function ChangeRequestForm() {
  const queryClient = useQueryClient()
  const now = new Date()
  const monthFrom = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`
  const monthTo = new Date(now.getFullYear(), now.getMonth() + 1, 0).toISOString().slice(0, 10)

  const { data: records = [] } = useQuery({
    queryKey: ['my-attendance-history-for-change-request', monthFrom, monthTo],
    queryFn: () => getMyAttendanceHistory(monthFrom, monthTo).then(r => r.data.data),
  })
  const { data: workplaces = [] } = useQuery({
    queryKey: ['my-assigned-workplaces-for-change-request'],
    queryFn: () => getMyAssignedWorkplaces().then(r => r.data.data),
  })

  const [recordId, setRecordId] = useState<number | ''>('')
  const [changeType, setChangeType] = useState<ChangeRequestType>('CHECK_IN_TIME')
  const [requestedCheckIn, setRequestedCheckIn] = useState('')
  const [requestedCheckOut, setRequestedCheckOut] = useState('')
  const [requestedWorkplaceId, setRequestedWorkplaceId] = useState<number | ''>('')
  const [reason, setReason] = useState('')

  const needsCheckIn = changeType === 'CHECK_IN_TIME'
  const needsCheckOut = changeType === 'CHECK_OUT_TIME'
  const needsWorkplace = changeType === 'WORKPLACE_CHANGE'

  const mutation = useMutation({
    mutationFn: () => {
      if (!recordId) throw new Error('대상 근태 기록을 선택해주세요.')
      if (!reason.trim()) throw new Error('사유를 입력해주세요.')
      return submitChangeRequest({
        recordId: recordId as number,
        changeType,
        reason,
        requestedCheckIn: needsCheckIn ? toIsoOrUndefined(requestedCheckIn) : undefined,
        requestedCheckOut: needsCheckOut ? toIsoOrUndefined(requestedCheckOut) : undefined,
        requestedWorkplaceId: needsWorkplace && requestedWorkplaceId ? Number(requestedWorkplaceId) : undefined,
      })
    },
    onSuccess: () => {
      toast.success('신청되었습니다.')
      setReason(''); setRequestedCheckIn(''); setRequestedCheckOut(''); setRequestedWorkplaceId(''); setRecordId('')
      queryClient.invalidateQueries({ queryKey: ['pending-requests'] })
      queryClient.invalidateQueries({ queryKey: ['request-history'] })
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? e?.message ?? '신청 실패'),
  })

  return (
    <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', padding: 24, maxWidth: 520 }}>
      <form onSubmit={e => { e.preventDefault(); mutation.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div>
          <label style={formLabelStyle}>대상 근태 기록 (이번 달)</label>
          <select value={recordId} onChange={e => setRecordId(e.target.value ? Number(e.target.value) : '')} required style={formInputStyle}>
            <option value="">선택해주세요</option>
            {records.map(r => (
              <option key={r.attendanceId} value={r.attendanceId}>
                {r.workDate.slice(5).replace('-', '/')}({weekdayLabel(r.workDate)}) — {RECORD_STATUS_LABEL[r.status] ?? r.status}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label style={formLabelStyle}>수정 유형</label>
          <select value={changeType} onChange={e => setChangeType(e.target.value as ChangeRequestType)} style={formInputStyle}>
            {(Object.entries(CHANGE_TYPE_LABEL) as [ChangeRequestType, string][]).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </div>

        {needsCheckIn && (
          <div>
            <label style={formLabelStyle}>요청 출근시각</label>
            <input type="datetime-local" value={requestedCheckIn} onChange={e => setRequestedCheckIn(e.target.value)} required style={formInputStyle} />
          </div>
        )}
        {needsCheckOut && (
          <div>
            <label style={formLabelStyle}>요청 퇴근시각</label>
            <input type="datetime-local" value={requestedCheckOut} onChange={e => setRequestedCheckOut(e.target.value)} required style={formInputStyle} />
          </div>
        )}
        {needsWorkplace && (
          <div>
            <label style={formLabelStyle}>요청 근무지</label>
            <select value={requestedWorkplaceId} onChange={e => setRequestedWorkplaceId(e.target.value ? Number(e.target.value) : '')} required style={formInputStyle}>
              <option value="">선택해주세요</option>
              {workplaces.map(w => <option key={w.id} value={w.id}>{w.name}</option>)}
            </select>
          </div>
        )}

        <div>
          <label style={formLabelStyle}>사유</label>
          <textarea value={reason} onChange={e => setReason(e.target.value)} required rows={3} style={{ ...formInputStyle, resize: 'vertical' }} />
        </div>

        <button type="submit" disabled={mutation.isPending} style={{ ...primarySubmitBtnStyle, opacity: mutation.isPending ? 0.6 : 1 }}>
          {mutation.isPending ? '신청 중...' : '신청'}
        </button>
      </form>
    </div>
  )
}

export default function ApprovalsPage() {
  const [tab, setTab] = useState<'SUBMIT' | 'PENDING' | 'HISTORY'>('PENDING')

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, color: '#1e293b', marginRight: 16 }}>승인함</h1>
        <button onClick={() => setTab('PENDING')} style={tab === 'PENDING' ? tabActiveStyle : tabStyle}>대기 중</button>
        <button onClick={() => setTab('SUBMIT')} style={tab === 'SUBMIT' ? tabActiveStyle : tabStyle}>신청하기</button>
        <button onClick={() => setTab('HISTORY')} style={tab === 'HISTORY' ? tabActiveStyle : tabStyle}>요청 이력</button>
      </div>

      {tab === 'SUBMIT' && <SubmitTab />}
      {tab === 'PENDING' && <PendingQueue />}
      {tab === 'HISTORY' && <RequestHistory />}
    </div>
  )
}

const PENDING_PAGE_SIZE = 15

function PendingQueue() {
  const queryClient = useQueryClient()
  const [comment, setComment] = useState<Record<string, string>>({})
  const [page, setPage] = useState(0)
  const level = useAuthStore(s => s.level)
  const canApprove = !!level && APPROVER_LEVELS.includes(level)
  const { data: scopeInfo } = useQuery({
    queryKey: ['attendance-scope-info'],
    queryFn: () => getAttendanceScopeInfo().then(r => r.data.data),
  })
  const isPartLeadOrAbove = !canApprove && !!scopeInfo && !scopeInfo.employeeLevel

  const canApproveItem = (item: ApprovalQueueItem): boolean => {
    if (canApprove) return true
    if (isPartLeadOrAbove && item.kind === 'CHANGE_REQUEST') {
      return PART_LEAD_APPROVABLE_CHANGE_TYPES.includes(item.changeRequest!.changeType)
    }
    return false
  }

  const changeRequestsQuery = useQuery({
    queryKey: ['pending-requests'],
    queryFn: () => getPendingChangeRequests().then((r) => r.data.data),
  })
  const leaveRequestsQuery = useQuery({
    queryKey: ['pending-leave-requests'],
    queryFn: () => getPendingLeaveRequests().then((r) => r.data.data),
  })
  const outsideWorkRequestsQuery = useQuery({
    queryKey: ['pending-outside-work-requests'],
    queryFn: () => getPendingOutsideWorkRequests().then((r) => r.data.data),
  })
  const workplaceChangeRequestsQuery = useQuery({
    queryKey: ['pending-workplace-change-requests'],
    queryFn: () => getPendingWorkplaceChangeRequests().then((r) => r.data.data),
  })
  const workScheduleChangeRequestsQuery = useQuery({
    queryKey: ['pending-work-schedule-change-requests'],
    queryFn: () => getPendingWorkScheduleChangeRequests().then((r) => r.data.data),
  })

  const isLoading = changeRequestsQuery.isLoading || leaveRequestsQuery.isLoading || outsideWorkRequestsQuery.isLoading || workplaceChangeRequestsQuery.isLoading || workScheduleChangeRequestsQuery.isLoading

  const items: ApprovalQueueItem[] = [
    ...(changeRequestsQuery.data ?? []).map((cr): ApprovalQueueItem => ({
      kind: 'CHANGE_REQUEST', id: cr.id, createdAt: cr.createdAt, changeRequest: cr,
    })),
    ...(leaveRequestsQuery.data ?? []).map((lr): ApprovalQueueItem => ({
      kind: 'LEAVE_REQUEST', id: lr.id, createdAt: lr.createdAt, leaveRequest: lr,
    })),
    ...(outsideWorkRequestsQuery.data ?? []).map((ow): ApprovalQueueItem => ({
      kind: 'OUTSIDE_WORK_REQUEST', id: ow.id, createdAt: ow.createdAt, outsideWorkRequest: ow,
    })),
    ...(workplaceChangeRequestsQuery.data ?? []).map((wc): ApprovalQueueItem => ({
      kind: 'WORKPLACE_CHANGE_REQUEST', id: wc.id, createdAt: wc.createdAt, workplaceChangeRequest: wc,
    })),
    ...(workScheduleChangeRequestsQuery.data ?? []).map((wsc): ApprovalQueueItem => ({
      kind: 'WORK_SCHEDULE_CHANGE_REQUEST', id: wsc.id, createdAt: wsc.createdAt, workScheduleChangeRequest: wsc,
    })),
  ].sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())

  // 승인·반려로 목록이 줄어들어 현재 페이지가 범위를 벗어나면(예: 마지막 페이지에서 전부 처리한 경우)
  // 존재하는 마지막 페이지로 자동 보정한다.
  const totalPages = Math.max(1, Math.ceil(items.length / PENDING_PAGE_SIZE))
  const currentPage = Math.min(page, totalPages - 1)
  const pagedItems = items.slice(currentPage * PENDING_PAGE_SIZE, (currentPage + 1) * PENDING_PAGE_SIZE)

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ['pending-requests'] })
    queryClient.invalidateQueries({ queryKey: ['pending-leave-requests'] })
    queryClient.invalidateQueries({ queryKey: ['pending-outside-work-requests'] })
    queryClient.invalidateQueries({ queryKey: ['pending-workplace-change-requests'] })
    queryClient.invalidateQueries({ queryKey: ['pending-work-schedule-change-requests'] })
    queryClient.invalidateQueries({ queryKey: ['request-history'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }

  const changeMutation = useMutation({
    mutationFn: ({ id, action, c }: { id: number; action: 'APPROVE' | 'REJECT'; c?: string }) =>
      processChangeRequest(id, action, c),
    onSuccess: (_, vars) => { toast.success(vars.action === 'APPROVE' ? '승인되었습니다.' : '반려되었습니다.'); invalidateAll() },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '처리 실패'),
  })
  const leaveMutation = useMutation({
    mutationFn: ({ id, action, c }: { id: number; action: 'APPROVE' | 'REJECT'; c?: string }) =>
      processLeaveRequest(id, action, c),
    onSuccess: (_, vars) => { toast.success(vars.action === 'APPROVE' ? '승인되었습니다.' : '반려되었습니다.'); invalidateAll() },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '처리 실패'),
  })
  const outsideWorkMutation = useMutation({
    mutationFn: ({ id, action, c }: { id: number; action: 'APPROVE' | 'REJECT'; c?: string }) =>
      processOutsideWorkRequest(id, action, c),
    onSuccess: (_, vars) => { toast.success(vars.action === 'APPROVE' ? '승인되었습니다.' : '반려되었습니다.'); invalidateAll() },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '처리 실패'),
  })
  const workplaceChangeMutation = useMutation({
    mutationFn: ({ id, action, c }: { id: number; action: 'APPROVE' | 'REJECT'; c?: string }) =>
      processWorkplaceChangeRequest(id, action, c),
    onSuccess: (_, vars) => { toast.success(vars.action === 'APPROVE' ? '승인되었습니다.' : '반려되었습니다.'); invalidateAll() },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '처리 실패'),
  })
  const workScheduleChangeMutation = useMutation({
    mutationFn: ({ id, action, c }: { id: number; action: 'APPROVE' | 'REJECT'; c?: string }) =>
      processWorkScheduleChangeRequest(id, action, c),
    onSuccess: (_, vars) => { toast.success(vars.action === 'APPROVE' ? '승인되었습니다.' : '반려되었습니다.'); invalidateAll() },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '처리 실패'),
  })

  const isPending = changeMutation.isPending || leaveMutation.isPending || outsideWorkMutation.isPending || workplaceChangeMutation.isPending || workScheduleChangeMutation.isPending

  const handle = (item: ApprovalQueueItem, action: 'APPROVE' | 'REJECT') => {
    const key = `${item.kind}-${item.id}`
    const c = comment[key]
    if (item.kind === 'CHANGE_REQUEST') changeMutation.mutate({ id: item.id, action, c })
    else if (item.kind === 'LEAVE_REQUEST') leaveMutation.mutate({ id: item.id, action, c })
    else if (item.kind === 'OUTSIDE_WORK_REQUEST') outsideWorkMutation.mutate({ id: item.id, action, c })
    else if (item.kind === 'WORKPLACE_CHANGE_REQUEST') workplaceChangeMutation.mutate({ id: item.id, action, c })
    else workScheduleChangeMutation.mutate({ id: item.id, action, c })
  }

  return (
    <div>
      {isLoading && <p style={{ color: '#64748b' }}>로딩 중...</p>}

      {!isLoading && items.length === 0 && (
        <div style={{
          background: '#fff', borderRadius: 12, padding: 48,
          textAlign: 'center', color: '#64748b',
          boxShadow: '0 1px 6px rgba(0,0,0,0.06)',
        }}>
          <p style={{ fontSize: 40, marginBottom: 12 }}>🎉</p>
          <p style={{ fontSize: 16, fontWeight: 500 }}>승인 대기 요청이 없습니다.</p>
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {pagedItems.map((item) => {
          const key = `${item.kind}-${item.id}`
          return (
            <div key={key} style={{
              background: '#fff', borderRadius: 12, padding: 24,
              boxShadow: '0 1px 6px rgba(0,0,0,0.06)',
              borderLeft: `4px solid ${KIND_COLOR[item.kind]}`,
            }}>
              <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 8 }}>
                <span style={{
                  fontSize: 12, fontWeight: 600, padding: '2px 10px', borderRadius: 20,
                  background: KIND_COLOR[item.kind] + '20', color: KIND_COLOR[item.kind],
                }}>
                  {item.kind === 'CHANGE_REQUEST' && CHANGE_TYPE_LABEL[item.changeRequest!.changeType]}
                  {item.kind === 'LEAVE_REQUEST' && `휴가 · ${LEAVE_TYPE_LABEL[item.leaveRequest!.requestType]}`}
                  {item.kind === 'OUTSIDE_WORK_REQUEST' && `외근·출장 · ${OUTSIDE_WORK_TYPE_LABEL[item.outsideWorkRequest!.requestType]}`}
                  {item.kind === 'WORKPLACE_CHANGE_REQUEST' && '근무지 변경요청'}
                  {item.kind === 'WORK_SCHEDULE_CHANGE_REQUEST' && '근무제 변경요청'}
                </span>
                <span style={{ fontSize: 13, fontWeight: 600, color: '#1e293b' }}>{requesterLabel(item)}</span>
                <span style={{ fontSize: 12, color: '#94a3b8' }}>#{item.id}</span>
              </div>

              {item.kind === 'CHANGE_REQUEST' && (
                <>
                  <p style={{ fontSize: 14, color: '#374151', marginBottom: 4 }}>
                    <b>대상일:</b> {item.changeRequest!.targetDate}
                  </p>
                  {item.changeRequest!.requestedCheckIn && (
                    <p style={{ fontSize: 13, color: '#64748b' }}>요청 출근: {formatDate(item.changeRequest!.requestedCheckIn)}</p>
                  )}
                  {item.changeRequest!.requestedCheckOut && (
                    <p style={{ fontSize: 13, color: '#64748b' }}>요청 퇴근: {formatDate(item.changeRequest!.requestedCheckOut)}</p>
                  )}
                  <p style={{ fontSize: 13, color: '#64748b', marginTop: 6 }}><b>사유:</b> {item.changeRequest!.reason}</p>
                </>
              )}

              {item.kind === 'LEAVE_REQUEST' && (
                <>
                  <p style={{ fontSize: 14, color: '#374151', marginBottom: 4 }}>
                    <b>기간:</b> {formatDate(item.leaveRequest!.startAt)} ~ {formatDate(item.leaveRequest!.endAt)}
                  </p>
                  <p style={{ fontSize: 13, color: '#64748b', marginTop: 6 }}><b>사유:</b> {item.leaveRequest!.reason}</p>
                </>
              )}

              {item.kind === 'OUTSIDE_WORK_REQUEST' && (
                <>
                  <p style={{ fontSize: 14, color: '#374151', marginBottom: 4 }}>
                    <b>기간:</b> {formatDate(item.outsideWorkRequest!.startAt)} ~ {formatDate(item.outsideWorkRequest!.endAt)}
                  </p>
                  {item.outsideWorkRequest!.destinationAddress && (
                    <p style={{ fontSize: 13, color: '#64748b' }}>목적지: {item.outsideWorkRequest!.destinationAddress}</p>
                  )}
                  {item.outsideWorkRequest!.clientName && (
                    <p style={{ fontSize: 13, color: '#64748b' }}>고객사: {item.outsideWorkRequest!.clientName}</p>
                  )}
                  <p style={{ fontSize: 13, color: '#64748b', marginTop: 6 }}><b>사유:</b> {item.outsideWorkRequest!.reason}</p>
                </>
              )}

              {item.kind === 'WORKPLACE_CHANGE_REQUEST' && (
                <>
                  <p style={{ fontSize: 14, color: '#374151', marginBottom: 4 }}>
                    <b>새 근무지:</b> {item.workplaceChangeRequest!.name}
                    {item.workplaceChangeRequest!.currentWorkplaceName && ` (기존: ${item.workplaceChangeRequest!.currentWorkplaceName})`}
                  </p>
                  {item.workplaceChangeRequest!.address && (
                    <p style={{ fontSize: 13, color: '#64748b' }}>주소: {item.workplaceChangeRequest!.address}{item.workplaceChangeRequest!.detailAddress ? ` ${item.workplaceChangeRequest!.detailAddress}` : ''}</p>
                  )}
                  <p style={{ fontSize: 13, color: '#64748b' }}>적용 예정일: {item.workplaceChangeRequest!.effectiveDate}</p>
                  <p style={{ fontSize: 13, color: '#64748b', marginTop: 6 }}><b>사유:</b> {item.workplaceChangeRequest!.reason}</p>
                </>
              )}

              {item.kind === 'WORK_SCHEDULE_CHANGE_REQUEST' && (
                <>
                  <p style={{ fontSize: 14, color: '#374151', marginBottom: 4 }}>
                    <b>변경할 근무제:</b> {item.workScheduleChangeRequest!.targetWorkScheduleName ?? `#${item.workScheduleChangeRequest!.targetWorkScheduleId}`}
                    {item.workScheduleChangeRequest!.currentWorkScheduleName && ` (기존: ${item.workScheduleChangeRequest!.currentWorkScheduleName})`}
                  </p>
                  <p style={{ fontSize: 13, color: '#64748b' }}>적용 예정월: {item.workScheduleChangeRequest!.effectiveMonth}</p>
                  <p style={{ fontSize: 13, color: '#64748b', marginTop: 6 }}><b>사유:</b> {item.workScheduleChangeRequest!.reason}</p>
                </>
              )}

              <p style={{ fontSize: 12, color: '#94a3b8', marginTop: 4 }}>신청: {formatDate(item.createdAt)}</p>

              {canApproveItem(item) && (
                <div style={{ marginTop: 16, display: 'flex', gap: 8, alignItems: 'center' }}>
                  <input
                    value={comment[key] ?? ''}
                    onChange={(e) => setComment((prev) => ({ ...prev, [key]: e.target.value }))}
                    placeholder="코멘트 (선택)"
                    style={{ flex: 1, padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 13 }}
                  />
                  <button
                    onClick={() => handle(item, 'APPROVE')}
                    disabled={isPending}
                    style={{ padding: '8px 20px', background: '#10b981', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 600 }}
                  >승인</button>
                  <button
                    onClick={() => handle(item, 'REJECT')}
                    disabled={isPending}
                    style={{ padding: '8px 20px', background: '#ef4444', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 600 }}
                  >반려</button>
                </div>
              )}
            </div>
          )
        })}
      </div>

      {items.length > 0 && (
        <div style={{ marginTop: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={currentPage <= 0}
            style={pageBtnStyle}
          >이전</button>
          <span style={{ fontSize: 12, color: '#94a3b8' }}>
            {currentPage + 1} / {totalPages} 페이지 (총 {items.length}건)
          </span>
          <button
            onClick={() => setPage((p) => p + 1)}
            disabled={currentPage + 1 >= totalPages}
            style={pageBtnStyle}
          >다음</button>
        </div>
      )}
    </div>
  )
}

function RequestHistory() {
  const [kind, setKind] = useState<HistoryKind>('ALL')
  const [status, setStatus] = useState<ChangeRequestStatus | ''>('')
  const [page, setPage] = useState(0)

  const changeQuery = useQuery({
    queryKey: ['request-history', 'CHANGE_REQUEST', status, kind === 'ALL' ? 0 : page],
    queryFn: () => getChangeRequestHistory({ status: status || undefined, page: kind === 'ALL' ? 0 : page, size: 15 }).then((r) => r.data.data),
    enabled: kind === 'ALL' || kind === 'CHANGE_REQUEST',
  })
  const leaveQuery = useQuery({
    queryKey: ['request-history', 'LEAVE_REQUEST', status, kind === 'ALL' ? 0 : page],
    queryFn: () => getLeaveRequestHistory({ status: status || undefined, page: kind === 'ALL' ? 0 : page, size: 15 }).then((r) => r.data.data),
    enabled: kind === 'ALL' || kind === 'LEAVE_REQUEST',
  })
  const outsideWorkQuery = useQuery({
    queryKey: ['request-history', 'OUTSIDE_WORK_REQUEST', status, kind === 'ALL' ? 0 : page],
    queryFn: () => getOutsideWorkRequestHistory({ status: status || undefined, page: kind === 'ALL' ? 0 : page, size: 15 }).then((r) => r.data.data),
    enabled: kind === 'ALL' || kind === 'OUTSIDE_WORK_REQUEST',
  })
  const workplaceChangeQuery = useQuery({
    queryKey: ['request-history', 'WORKPLACE_CHANGE_REQUEST', status, kind === 'ALL' ? 0 : page],
    queryFn: () => getWorkplaceChangeRequestHistory({ status: status || undefined, page: kind === 'ALL' ? 0 : page, size: 15 }).then((r) => r.data.data),
    enabled: kind === 'ALL' || kind === 'WORKPLACE_CHANGE_REQUEST',
  })
  const workScheduleChangeQuery = useQuery({
    queryKey: ['request-history', 'WORK_SCHEDULE_CHANGE_REQUEST', status, kind === 'ALL' ? 0 : page],
    queryFn: () => getWorkScheduleChangeRequestHistory({ status: status || undefined, page: kind === 'ALL' ? 0 : page, size: 15 }).then((r) => r.data.data),
    enabled: kind === 'ALL' || kind === 'WORK_SCHEDULE_CHANGE_REQUEST',
  })

  const isLoading = changeQuery.isLoading || leaveQuery.isLoading || outsideWorkQuery.isLoading || workplaceChangeQuery.isLoading || workScheduleChangeQuery.isLoading

  let items: ApprovalQueueItem[] = []
  let activePage: { number: number; totalPages: number; totalElements: number } | null = null

  if (kind === 'ALL') {
    items = [
      ...(changeQuery.data?.content ?? []).map((cr): ApprovalQueueItem => ({
        kind: 'CHANGE_REQUEST', id: cr.id, createdAt: cr.createdAt, changeRequest: cr,
      })),
      ...(leaveQuery.data?.content ?? []).map((lr): ApprovalQueueItem => ({
        kind: 'LEAVE_REQUEST', id: lr.id, createdAt: lr.createdAt, leaveRequest: lr,
      })),
      ...(outsideWorkQuery.data?.content ?? []).map((ow): ApprovalQueueItem => ({
        kind: 'OUTSIDE_WORK_REQUEST', id: ow.id, createdAt: ow.createdAt, outsideWorkRequest: ow,
      })),
      ...(workplaceChangeQuery.data?.content ?? []).map((wc): ApprovalQueueItem => ({
        kind: 'WORKPLACE_CHANGE_REQUEST', id: wc.id, createdAt: wc.createdAt, workplaceChangeRequest: wc,
      })),
      ...(workScheduleChangeQuery.data?.content ?? []).map((wsc): ApprovalQueueItem => ({
        kind: 'WORK_SCHEDULE_CHANGE_REQUEST', id: wsc.id, createdAt: wsc.createdAt, workScheduleChangeRequest: wsc,
      })),
    ].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
  } else if (kind === 'CHANGE_REQUEST' && changeQuery.data) {
    items = changeQuery.data.content.map((cr): ApprovalQueueItem => ({
      kind: 'CHANGE_REQUEST', id: cr.id, createdAt: cr.createdAt, changeRequest: cr,
    }))
    activePage = changeQuery.data
  } else if (kind === 'LEAVE_REQUEST' && leaveQuery.data) {
    items = leaveQuery.data.content.map((lr): ApprovalQueueItem => ({
      kind: 'LEAVE_REQUEST', id: lr.id, createdAt: lr.createdAt, leaveRequest: lr,
    }))
    activePage = leaveQuery.data
  } else if (kind === 'OUTSIDE_WORK_REQUEST' && outsideWorkQuery.data) {
    items = outsideWorkQuery.data.content.map((ow): ApprovalQueueItem => ({
      kind: 'OUTSIDE_WORK_REQUEST', id: ow.id, createdAt: ow.createdAt, outsideWorkRequest: ow,
    }))
    activePage = outsideWorkQuery.data
  } else if (kind === 'WORKPLACE_CHANGE_REQUEST' && workplaceChangeQuery.data) {
    items = workplaceChangeQuery.data.content.map((wc): ApprovalQueueItem => ({
      kind: 'WORKPLACE_CHANGE_REQUEST', id: wc.id, createdAt: wc.createdAt, workplaceChangeRequest: wc,
    }))
    activePage = workplaceChangeQuery.data
  } else if (kind === 'WORK_SCHEDULE_CHANGE_REQUEST' && workScheduleChangeQuery.data) {
    items = workScheduleChangeQuery.data.content.map((wsc): ApprovalQueueItem => ({
      kind: 'WORK_SCHEDULE_CHANGE_REQUEST', id: wsc.id, createdAt: wsc.createdAt, workScheduleChangeRequest: wsc,
    }))
    activePage = workScheduleChangeQuery.data
  }

  const statusOf = (item: ApprovalQueueItem): ChangeRequestStatus =>
    (item.changeRequest?.status ?? item.leaveRequest?.status ?? item.outsideWorkRequest?.status ?? item.workplaceChangeRequest?.status ?? item.workScheduleChangeRequest?.status)!

  const approverOf = (item: ApprovalQueueItem): string =>
    item.changeRequest?.approverName ?? item.leaveRequest?.approverName ?? item.outsideWorkRequest?.approverName
      ?? item.workplaceChangeRequest?.approverName ?? item.workScheduleChangeRequest?.approverName ?? '-'

  const handleKindChange = (k: HistoryKind) => { setKind(k); setPage(0) }
  const handleStatusChange = (s: ChangeRequestStatus | '') => { setStatus(s); setPage(0) }

  return (
    <div>
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <select value={kind} onChange={(e) => handleKindChange(e.target.value as HistoryKind)} style={selectStyle}>
          <option value="ALL">전체 구분</option>
          <option value="CHANGE_REQUEST">근태 수정</option>
          <option value="LEAVE_REQUEST">휴가</option>
          <option value="OUTSIDE_WORK_REQUEST">외근·출장</option>
          <option value="WORKPLACE_CHANGE_REQUEST">근무지 변경요청</option>
          <option value="WORK_SCHEDULE_CHANGE_REQUEST">근무제 변경요청</option>
        </select>
        <select value={status} onChange={(e) => handleStatusChange(e.target.value as ChangeRequestStatus | '')} style={selectStyle}>
          <option value="">전체 상태</option>
          <option value="PENDING">대기</option>
          <option value="APPROVED">승인</option>
          <option value="REJECTED">반려</option>
          <option value="CANCELED">취소</option>
        </select>
      </div>

      <div style={{ background: '#fff', borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,0.06)', overflow: 'hidden' }}>
        {isLoading ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>로딩 중...</div>
        ) : items.length === 0 ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>요청 이력이 없습니다.</div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#f8fafc' }}>
                {['구분', '상세', '신청자', '내용', '사유', '상태', '승인자', '신청일시'].map((h) => (
                  <th key={h} style={thStyle}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {items.map((item) => {
                const s = statusOf(item)
                return (
                  <tr key={`${item.kind}-${item.id}`} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={tdStyle}>
                      <span style={{
                        padding: '2px 8px', borderRadius: 4, fontSize: 12, fontWeight: 600,
                        background: KIND_COLOR[item.kind] + '20', color: KIND_COLOR[item.kind],
                      }}>{KIND_LABEL[item.kind]}</span>
                    </td>
                    <td style={tdStyle}>{detailText(item)}</td>
                    <td style={{ ...tdStyle, fontWeight: 600 }}>{requesterLabel(item)}</td>
                    <td style={tdStyle}>{summaryText(item)}</td>
                    <td style={{ ...tdStyle, maxWidth: 280 }}>{reasonText(item)}</td>
                    <td style={tdStyle}>
                      <span style={{
                        padding: '2px 8px', borderRadius: 4, fontSize: 12, fontWeight: 600,
                        background: STATUS_COLOR[s] + '20', color: STATUS_COLOR[s],
                      }}>{STATUS_LABEL[s]}</span>
                    </td>
                    <td style={tdStyle}>{approverOf(item)}</td>
                    <td style={{ ...tdStyle, whiteSpace: 'nowrap', fontSize: 12 }}>{formatDate(item.createdAt)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>

      {kind === 'ALL' ? (
        <p style={{ marginTop: 10, fontSize: 12, color: '#94a3b8' }}>구분별 최근 15건씩 표시됩니다. 특정 구분을 선택하면 전체 이력을 페이지로 조회할 수 있습니다.</p>
      ) : activePage && (
        <div style={{ marginTop: 10, display: 'flex', alignItems: 'center', gap: 8 }}>
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={activePage.number <= 0}
            style={pageBtnStyle}
          >이전</button>
          <span style={{ fontSize: 12, color: '#94a3b8' }}>
            {activePage.number + 1} / {Math.max(1, activePage.totalPages)} 페이지 (총 {activePage.totalElements}건)
          </span>
          <button
            onClick={() => setPage((p) => p + 1)}
            disabled={activePage.number + 1 >= activePage.totalPages}
            style={pageBtnStyle}
          >다음</button>
        </div>
      )}
    </div>
  )
}

const tabStyle: React.CSSProperties = { padding: '8px 16px', background: '#fff', color: '#64748b', border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontSize: 14, fontWeight: 600 }
const tabActiveStyle: React.CSSProperties = { ...tabStyle, background: '#2563eb', color: '#fff', border: '1px solid #2563eb' }
const subTabStyle: React.CSSProperties = { padding: '6px 14px', background: '#f8fafc', color: '#64748b', border: '1px solid #e2e8f0', borderRadius: 20, cursor: 'pointer', fontSize: 13, fontWeight: 600 }
const subTabActiveStyle: React.CSSProperties = { ...subTabStyle, background: '#1e293b', color: '#fff', border: '1px solid #1e293b' }
const selectStyle: React.CSSProperties = { padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 13, background: '#fff' }
const thStyle: React.CSSProperties = { padding: '12px 16px', textAlign: 'left', fontSize: 12, fontWeight: 600, color: '#64748b', borderBottom: '1px solid #e2e8f0' }
const tdStyle: React.CSSProperties = { padding: '12px 16px', fontSize: 14, color: '#374151' }
const pageBtnStyle: React.CSSProperties = { padding: '6px 14px', background: '#fff', color: '#374151', border: '1px solid #d1d5db', borderRadius: 8, cursor: 'pointer', fontSize: 13 }
const formLabelStyle: React.CSSProperties = { display: 'block', fontSize: 13, fontWeight: 500, color: '#374151', marginBottom: 4 }
const formInputStyle: React.CSSProperties = { width: '100%', padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' }
const primarySubmitBtnStyle: React.CSSProperties = { padding: '10px 0', background: '#2563eb', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 14, fontWeight: 700 }
