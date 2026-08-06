import client from './client'
import type { ApiResponse, ChangeRequestStatus, LeaveRequest, LeaveRequestType, PageResponse } from '@/types'

export interface LeaveRequestPayload {
  requestType: LeaveRequestType
  startAt: string
  endAt: string
  reason: string
}

export const getPendingLeaveRequests = () =>
  client.get<ApiResponse<LeaveRequest[]>>('/leave-requests/pending')

export const getMyLeaveRequests = () =>
  client.get<ApiResponse<LeaveRequest[]>>('/leave-requests/my')

export const submitLeaveRequest = (payload: LeaveRequestPayload) =>
  client.post<ApiResponse<LeaveRequest>>('/leave-requests', payload)

export const processLeaveRequest = (requestId: number, action: 'APPROVE' | 'REJECT', comment?: string) =>
  client.patch<ApiResponse<LeaveRequest>>(`/leave-requests/${requestId}`, { action, comment })

export const getLeaveRequestHistory = (params?: { status?: ChangeRequestStatus; page?: number; size?: number }) =>
  client.get<ApiResponse<PageResponse<LeaveRequest>>>('/admin/leave-requests', { params: { page: 0, size: 20, ...params } })

export const getApprovedLeaveCalendar = (year: number, month: number) =>
  client.get<ApiResponse<LeaveRequest[]>>('/admin/leave-requests/calendar', { params: { year, month } })

export interface BulkLeaveRowResult {
  rowNumber: number
  name: string
  success: boolean
  message: string
}

export interface BulkLeaveImportResponse {
  totalRows: number
  successCount: number
  failureCount: number
  results: BulkLeaveRowResult[]
}

export const bulkUploadLeaveRequests = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return client.post<ApiResponse<BulkLeaveImportResponse>>('/leave-requests/bulk', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export const downloadLeaveBulkImportTemplate = async (): Promise<void> => {
  const res = await client.get('/leave-requests/bulk/template', { responseType: 'blob' })
  const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = 'leave_bulk_template.xlsx'; a.click()
  URL.revokeObjectURL(url)
}
