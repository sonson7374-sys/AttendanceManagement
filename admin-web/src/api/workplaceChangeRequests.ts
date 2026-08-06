import client from './client'
import type { ApiResponse, ChangeRequestStatus, PageResponse, WorkplaceChangeRequest } from '@/types'

export interface WorkplaceChangeRequestPayload {
  currentWorkplaceId?: number
  name: string
  address?: string
  detailAddress?: string
  type?: string
  latitude: number
  longitude: number
  radiusMeters?: number
  maxAccuracyMeters?: number
  checkInAllowed?: boolean
  checkOutAllowed?: boolean
  effectiveDate: string
  reason: string
}

export const getPendingWorkplaceChangeRequests = () =>
  client.get<ApiResponse<WorkplaceChangeRequest[]>>('/workplace-change-requests/pending')

export const getMyWorkplaceChangeRequests = () =>
  client.get<ApiResponse<WorkplaceChangeRequest[]>>('/workplace-change-requests/my')

export const submitWorkplaceChangeRequest = (payload: WorkplaceChangeRequestPayload) =>
  client.post<ApiResponse<WorkplaceChangeRequest>>('/workplace-change-requests', payload)

export const processWorkplaceChangeRequest = (requestId: number, action: 'APPROVE' | 'REJECT', comment?: string) =>
  client.patch<ApiResponse<WorkplaceChangeRequest>>(`/workplace-change-requests/${requestId}`, { action, comment })

export const getWorkplaceChangeRequestHistory = (params?: { status?: ChangeRequestStatus; page?: number; size?: number }) =>
  client.get<ApiResponse<PageResponse<WorkplaceChangeRequest>>>('/admin/workplace-change-requests', { params: { page: 0, size: 20, ...params } })
