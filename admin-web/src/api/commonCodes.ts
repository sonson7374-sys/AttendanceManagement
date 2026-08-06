import client from './client'
import type { ApiResponse } from '@/types'

export interface CommonCode {
  id: number
  groupCode: string
  code: string
  codeName: string
  description?: string
  displayOrder: number
  active: boolean
  protectedCode: boolean
}

export interface CommonCodeCreatePayload {
  groupCode: string
  code: string
  codeName: string
  description?: string
  displayOrder: number
}

export interface CommonCodeUpdatePayload {
  codeName: string
  description?: string
  displayOrder: number
  active: boolean
}

export const getCommonCodes = (groupCode: string) =>
  client.get<ApiResponse<CommonCode[]>>('/admin/common-codes', { params: { groupCode } })

export const createCommonCode = (payload: CommonCodeCreatePayload) =>
  client.post<ApiResponse<CommonCode>>('/admin/common-codes', payload)

export const updateCommonCode = (id: number, payload: CommonCodeUpdatePayload) =>
  client.put<ApiResponse<CommonCode>>(`/admin/common-codes/${id}`, payload)

export const deleteCommonCode = (id: number) =>
  client.delete<ApiResponse<void>>(`/admin/common-codes/${id}`)
