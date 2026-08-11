import client from './client'
import type { ApiResponse } from '@/types'

export interface Company {
  id: number
  name: string
  businessNumber?: string
  address?: string
  phone?: string
  active: boolean
  createdAt: string
}

export interface CompanyCreatePayload {
  name: string
  businessNumber?: string
  address?: string
  phone?: string
  adminEmail: string
  adminName: string
  adminEmployeeNumber?: string
}

export interface CompanyCreateResult extends Company {
  adminEmail: string
  temporaryPassword: string
}

export interface CompanyUpdatePayload {
  name: string
  businessNumber?: string
  address?: string
  phone?: string
}

// 회사 목록 조회는 예외적으로 회사 경계를 넘는다 — 새 회사를 만들려면 이미 어떤 회사들이
// 있는지 봐야 하기 때문이다(다른 업무 데이터는 여전히 완전히 격리되어 있음).
export const getCompanies = () =>
  client.get<ApiResponse<Company[]>>('/companies')

// 로그인한 사용자 본인의 소속 회사 정보. 전 직원이 조회할 수 있다(사이드바 표시용).
export const getMyCompany = () =>
  client.get<ApiResponse<Company>>('/companies/me')

export const createCompany = (payload: CompanyCreatePayload) =>
  client.post<ApiResponse<CompanyCreateResult>>('/companies', payload)

export const updateCompany = (id: number, payload: CompanyUpdatePayload) =>
  client.put<ApiResponse<Company>>(`/companies/${id}`, payload)
