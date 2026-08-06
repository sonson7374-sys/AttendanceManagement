import client from './client'
import type { ApiResponse, Organization } from '@/types'

export const getOrganizations = (companyId = 1) =>
  client.get<ApiResponse<Organization[]>>('/organizations', { params: { companyId } })

export const createOrganization = (payload: { name: string; parentId?: number; displayOrder?: number }) =>
  client.post<ApiResponse<Organization>>('/organizations', payload)

export const updateOrganization = (id: number, payload: { name: string; parentId?: number; displayOrder?: number }) =>
  client.put<ApiResponse<Organization>>(`/organizations/${id}`, payload)

export const deleteOrganization = (id: number) =>
  client.delete<ApiResponse<void>>(`/organizations/${id}`)
