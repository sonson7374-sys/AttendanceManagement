import client from './client'
import type { ApiResponse, Organization } from '@/types'

// 조회 대상 회사는 서버가 로그인한 사용자의 소속 회사로 강제한다(클라이언트가 companyId를 지정할 수 없음).
export const getOrganizations = () =>
  client.get<ApiResponse<Organization[]>>('/organizations')

export const createOrganization = (payload: { name: string; parentId?: number; displayOrder?: number }) =>
  client.post<ApiResponse<Organization>>('/organizations', payload)

export const updateOrganization = (id: number, payload: { name: string; parentId?: number; displayOrder?: number }) =>
  client.put<ApiResponse<Organization>>(`/organizations/${id}`, payload)

export const deleteOrganization = (id: number) =>
  client.delete<ApiResponse<void>>(`/organizations/${id}`)
