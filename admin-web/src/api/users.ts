import client from './client'
import type { ApiResponse, PageResponse, User, UserDevice, UserRole } from '@/types'

export interface CreateUserPayload {
  email: string
  password: string
  name: string
  employeeNumber: string
  role: UserRole
  level: string
  companyId: number
  organizationId?: number
}

export const getUsers = (page = 0, size = 20, filters?: { organizationId?: number; name?: string }) =>
  client.get<ApiResponse<PageResponse<User>>>('/users', {
    params: { page, size, sort: 'id,desc', organizationId: filters?.organizationId, name: filters?.name },
  })

export const getUser = (userId: number) =>
  client.get<ApiResponse<User>>(`/users/${userId}`)

export const createUser = (payload: CreateUserPayload) =>
  client.post<ApiResponse<User>>('/users', payload)

export const lockUser = (userId: number) =>
  client.post<ApiResponse<void>>(`/users/${userId}/lock`)

export const unlockUser = (userId: number) =>
  client.post<ApiResponse<void>>(`/users/${userId}/unlock`)

export const resignUser = (userId: number, resignDate: string) =>
  client.post<ApiResponse<void>>(`/users/${userId}/resign`, { resignDate })

// 퇴사 처리와 달리 되돌릴 수 없는 완전 삭제. 출퇴근·신청 등 사용 이력이 있으면 서버가 거부한다.
export const deleteUser = (userId: number) =>
  client.delete<ApiResponse<void>>(`/users/${userId}`)

export interface PasswordResetResult {
  userId: number
  temporaryPassword: string
}

export const resetUserPassword = (userId: number) =>
  client.post<ApiResponse<PasswordResetResult>>(`/users/${userId}/reset-password`)

// 시스템관리자가 직접 새 비밀번호를 지정 (임시 비밀번호 생성 없이, 현재 비밀번호 불필요)
export const setUserPassword = (userId: number, newPassword: string) =>
  client.patch<ApiResponse<void>>(`/users/${userId}/password`, { newPassword })

export const listUserDevices = (userId: number) =>
  client.get<ApiResponse<UserDevice[]>>(`/users/${userId}/devices`)

export const revokeUserDevice = (userId: number, deviceId: string) =>
  client.delete<ApiResponse<void>>(`/users/${userId}/devices/${deviceId}`)

export interface UpdateProfilePayload {
  name: string
  phone?: string
  jobTitle?: string
  employeeNumber?: string
  organizationId?: number
  employmentType?: string
  hireDate?: string
  level: string
}

export const updateUserProfile = (userId: number, payload: UpdateProfilePayload) =>
  client.patch<ApiResponse<User>>(`/users/${userId}/profile`, payload)

export const changeUserRole = (userId: number, role: UserRole) =>
  client.patch<ApiResponse<User>>(`/users/${userId}/role`, { role })

export interface BulkUserRowResult {
  rowNumber: number
  email: string
  success: boolean
  message: string
}

export interface BulkUserImportResponse {
  totalRows: number
  successCount: number
  failureCount: number
  results: BulkUserRowResult[]
}

export const bulkCreateUsers = (companyId: number, file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return client.post<ApiResponse<BulkUserImportResponse>>('/users/bulk', formData, {
    params: { companyId },
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export const downloadBulkImportTemplate = async (): Promise<void> => {
  const res = await client.get('/users/bulk/template', { responseType: 'blob' })
  const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = 'employee_bulk_template.xlsx'; a.click()
  URL.revokeObjectURL(url)
}
