import client from './client'
import type { ApiResponse, ChangeRequestStatus, OutsideWorkRequest, OutsideWorkRequestType, PageResponse } from '@/types'

export interface OutsideWorkRequestPayload {
  requestType: OutsideWorkRequestType
  startAt: string
  endAt: string
  reason: string
  destinationAddress?: string
  destinationLatitude?: number
  destinationLongitude?: number
  tempRadiusMeters?: number
  visitPurpose?: string
  clientName?: string
  expectedReturnAt?: string
}

export const getPendingOutsideWorkRequests = () =>
  client.get<ApiResponse<OutsideWorkRequest[]>>('/outside-work-requests/pending')

export const getMyOutsideWorkRequests = () =>
  client.get<ApiResponse<OutsideWorkRequest[]>>('/outside-work-requests/my')

export const submitOutsideWorkRequest = (payload: OutsideWorkRequestPayload) =>
  client.post<ApiResponse<OutsideWorkRequest>>('/outside-work-requests', payload)

export const processOutsideWorkRequest = (requestId: number, action: 'APPROVE' | 'REJECT', comment?: string) =>
  client.patch<ApiResponse<OutsideWorkRequest>>(`/outside-work-requests/${requestId}`, { action, comment })

export const getOutsideWorkRequestHistory = (params?: { status?: ChangeRequestStatus; page?: number; size?: number }) =>
  client.get<ApiResponse<PageResponse<OutsideWorkRequest>>>('/admin/outside-work-requests', { params: { page: 0, size: 20, ...params } })
