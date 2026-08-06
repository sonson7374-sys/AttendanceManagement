import client from './client'
import type { ApiResponse, PageResponse, AttendanceRecord, AttendanceStatus, AttendanceBoardRow, AttendanceScopeInfo, DashboardStats, ChangeRequest, ChangeRequestStatus, MonthlyUserSummary, AuditLog } from '@/types'

export interface ManualAttendancePayload {
  userId: number
  workDate: string
  workplaceId?: number
  checkInAt?: string
  checkOutAt?: string
  status: AttendanceStatus
  workMinutes?: number
  breakMinutes?: number
  overtimeMinutes?: number
  reason: string
}

export interface AttendanceCorrectionPayload {
  checkInAt?: string
  checkOutAt?: string
  workplaceId?: number
  status?: AttendanceStatus
  workMinutes?: number
  breakMinutes?: number
  overtimeMinutes?: number
  reason: string
}

export const getDashboardStats = () =>
  client.get<ApiResponse<DashboardStats>>('/admin/dashboard')

export const getAttendanceScopeInfo = () =>
  client.get<ApiResponse<AttendanceScopeInfo>>('/admin/attendance/scope-info')

export interface DailyAttendanceFilters {
  workplaceId?: number
  organizationId?: number
  employeeName?: string
  status?: AttendanceStatus
  lateOnly?: boolean
  locationValid?: boolean
}

export const getDailyAttendance = (
  date: string, page = 0, size = 50, filters: DailyAttendanceFilters = {},
) =>
  client.get<ApiResponse<PageResponse<AttendanceRecord>>>('/admin/attendance/daily', {
    params: { date, page, size, ...filters },
  })

export interface RegisterBoardFilters {
  organizationId?: number
  employeeName?: string
}

export const getAttendanceRegisterBoard = (date: string, filters: RegisterBoardFilters = {}) =>
  client.get<ApiResponse<AttendanceBoardRow[]>>('/admin/attendance/register-board', {
    params: { date, ...filters },
  })

export const getPendingChangeRequests = () =>
  client.get<ApiResponse<ChangeRequest[]>>('/attendance/change-requests/pending')

export interface ChangeRequestSubmitPayload {
  recordId: number
  changeType: ChangeRequest['changeType']
  reason: string
  requestedCheckIn?: string
  requestedCheckOut?: string
  requestedWorkplaceId?: number
}

export const submitChangeRequest = (payload: ChangeRequestSubmitPayload) =>
  client.post<ApiResponse<ChangeRequest>>('/attendance/change-requests', payload)

export const processChangeRequest = (requestId: number, action: 'APPROVE' | 'REJECT', comment?: string) =>
  client.patch<ApiResponse<ChangeRequest>>(`/attendance/change-requests/${requestId}`, { action, comment })

export const getChangeRequestHistory = (params?: { status?: ChangeRequestStatus; page?: number; size?: number }) =>
  client.get<ApiResponse<PageResponse<ChangeRequest>>>('/admin/change-requests', { params: { page: 0, size: 20, ...params } })

export const getMonthlySummary = (year: number, month: number) =>
  client.get<ApiResponse<MonthlyUserSummary[]>>('/admin/attendance/monthly', { params: { year, month } })

export const getAuditLogs = (params?: { actorEmail?: string; from?: string; to?: string; page?: number; size?: number }) =>
  client.get<ApiResponse<PageResponse<AuditLog>>>('/admin/audit-logs', { params: { page: 0, size: 50, ...params } })

export const createManualAttendance = (payload: ManualAttendancePayload) =>
  client.post<ApiResponse<AttendanceRecord>>('/admin/attendance/manual', payload)

export const correctAttendance = (id: number, payload: AttendanceCorrectionPayload) =>
  client.put<ApiResponse<AttendanceRecord>>(`/admin/attendance/${id}`, payload)

export const closeAttendanceMonth = (year: number, month: number) =>
  client.post<ApiResponse<number>>('/admin/attendance/close', { year, month })

export const reopenAttendanceMonth = (year: number, month: number) =>
  client.post<ApiResponse<number>>('/admin/attendance/reopen', { year, month })

export const downloadMonthlyExcel = async (year: number, month: number): Promise<void> => {
  const res = await client.get('/admin/attendance/export', { params: { year, month }, responseType: 'blob' })
  const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = `attendance_${year}-${String(month).padStart(2, '0')}.xlsx`; a.click()
  URL.revokeObjectURL(url)
}

export const getDailyAttendanceCsv = async (date: string): Promise<void> => {
  const res = await getDailyAttendance(date, 0, 1000)
  const records = res.data.data?.content ?? []
  const header = '사번,이름,상태,출근,퇴근,근무지,출근거리(m),퇴근거리(m),정확도(m),처리방식,근무(분),초과(분)'
  const rows = records.map((r: AttendanceRecord) =>
    [r.employeeNumber, r.userName, r.status, r.checkInAt ?? '', r.checkOutAt ?? '',
     r.workplaceName ?? '', r.checkInDistanceMeters ?? '', r.checkOutDistanceMeters ?? '',
     r.checkInAccuracyMeters ?? '', r.processMethod ?? '', r.workMinutes ?? '', r.overtimeMinutes ?? ''].join(','))
  const csv = [header, ...rows].join('\n')
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = `attendance_${date}.csv`; a.click()
  URL.revokeObjectURL(url)
}
