import client from './client'
import type { ApiResponse } from '@/types'

export interface CommonCodeGroup {
  id: number
  groupCode: string
  groupName: string
  description?: string
  protectedGroup: boolean
}

export interface CommonCodeGroupCreatePayload {
  groupCode: string
  groupName: string
  description?: string
}

export interface CommonCodeGroupUpdatePayload {
  groupName: string
  description?: string
}

export const getCommonCodeGroups = () =>
  client.get<ApiResponse<CommonCodeGroup[]>>('/admin/common-code-groups')

export const createCommonCodeGroup = (payload: CommonCodeGroupCreatePayload) =>
  client.post<ApiResponse<CommonCodeGroup>>('/admin/common-code-groups', payload)

export const updateCommonCodeGroup = (id: number, payload: CommonCodeGroupUpdatePayload) =>
  client.put<ApiResponse<CommonCodeGroup>>(`/admin/common-code-groups/${id}`, payload)
