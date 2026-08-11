import client from './client'
import type { ApiResponse, Workplace, WorkplaceType } from '@/types'

export interface WorkplacePayload {
  companyId: number
  name: string
  address: string
  detailAddress?: string
  type: WorkplaceType
  latitude: number
  longitude: number
  radiusMeters: number
  maxAccuracyMeters?: number
  checkInAllowed: boolean
  checkOutAllowed: boolean
  validFrom?: string
  validTo?: string
}

export const getWorkplaces = (companyId: number, includeInactive = false) =>
  client.get<ApiResponse<Workplace[]>>('/workplaces', { params: { companyId, includeInactive } })

export const createWorkplace = (payload: WorkplacePayload) =>
  client.post<ApiResponse<Workplace>>('/workplaces', payload)

export const updateWorkplace = (id: number, payload: WorkplacePayload) =>
  client.put<ApiResponse<Workplace>>(`/workplaces/${id}`, payload)

// 물리 삭제가 아닌 비활성화 처리 (CLAUDE.md 데이터 규칙: 소프트 삭제/비활성화)
export const deactivateWorkplace = (id: number) =>
  client.delete<ApiResponse<void>>(`/workplaces/${id}`)

export const activateWorkplace = (id: number) =>
  client.post<ApiResponse<void>>(`/workplaces/${id}/activate`)

// 이미 비활성화(삭제)된 근무지만 대상. 출퇴근 기록 등 사용 이력이 있으면 서버가 거부한다.
export const permanentlyDeleteWorkplace = (id: number) =>
  client.delete<ApiResponse<void>>(`/workplaces/${id}/permanent`)

export const assignUserToWorkplace = (workplaceId: number, userId: number) =>
  client.post<ApiResponse<void>>(`/workplaces/${workplaceId}/users/${userId}`)

export const getAssignedUsers = (workplaceId: number) =>
  client.get<ApiResponse<import('@/types').User[]>>(`/workplaces/${workplaceId}/users`)

export const getWorkplacesForUser = (userId: number) =>
  client.get<ApiResponse<Workplace[]>>(`/workplaces/users/${userId}`)

export const removeUserFromWorkplace = (workplaceId: number, userId: number) =>
  client.delete<ApiResponse<void>>(`/workplaces/${workplaceId}/users/${userId}`)

export const bulkAssignUsersToWorkplace = (
  workplaceId: number, userIds: number[], validFrom?: string, validTo?: string,
) =>
  client.post<ApiResponse<void>>(`/workplaces/${workplaceId}/users/bulk`, { userIds, validFrom, validTo })
