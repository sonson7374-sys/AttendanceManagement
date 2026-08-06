import client from './client'
import type { ApiResponse, ChangeRequestStatus, PageResponse, WorkScheduleChangeRequest } from '@/types'

export interface WorkScheduleChangeRequestPayload {
  currentWorkScheduleId?: number
  targetWorkScheduleId: number
  effectiveMonth: string
  reason: string
}

export const getPendingWorkScheduleChangeRequests = () =>
  client.get<ApiResponse<WorkScheduleChangeRequest[]>>('/work-schedule-change-requests/pending')

export const getMyWorkScheduleChangeRequests = () =>
  client.get<ApiResponse<WorkScheduleChangeRequest[]>>('/work-schedule-change-requests/my')

export const submitWorkScheduleChangeRequest = (payload: WorkScheduleChangeRequestPayload) =>
  client.post<ApiResponse<WorkScheduleChangeRequest>>('/work-schedule-change-requests', payload)

export const processWorkScheduleChangeRequest = (requestId: number, action: 'APPROVE' | 'REJECT', comment?: string) =>
  client.patch<ApiResponse<WorkScheduleChangeRequest>>(`/work-schedule-change-requests/${requestId}`, { action, comment })

export const getWorkScheduleChangeRequestHistory = (params?: { status?: ChangeRequestStatus; page?: number; size?: number }) =>
  client.get<ApiResponse<PageResponse<WorkScheduleChangeRequest>>>('/admin/work-schedule-change-requests', { params: { page: 0, size: 20, ...params } })
