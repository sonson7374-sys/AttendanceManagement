import client from './client'
import type { ApiResponse, WorkSchedule } from '@/types'

export const getWorkSchedules = () =>
  client.get<ApiResponse<WorkSchedule[]>>('/work-schedules')

export const createWorkSchedule = (payload: Omit<WorkSchedule, 'id' | 'companyId' | 'active'>) =>
  client.post<ApiResponse<WorkSchedule>>('/work-schedules', payload)

export const updateWorkSchedule = (id: number, payload: Omit<WorkSchedule, 'id' | 'companyId' | 'active'>) =>
  client.put<ApiResponse<WorkSchedule>>(`/work-schedules/${id}`, payload)

export const deleteWorkSchedule = (id: number) =>
  client.delete<ApiResponse<void>>(`/work-schedules/${id}`)

export const getCurrentWorkScheduleForUser = (userId: number) =>
  client.get<ApiResponse<WorkSchedule>>(`/work-schedules/users/${userId}/current`)

// 로그인한 본인의 근무제 조회. EMPLOYEE 등 전체 근무제 목록 조회 권한이 없는 사용자도 호출 가능하다.
export const getMyWorkSchedule = () =>
  client.get<ApiResponse<WorkSchedule>>('/work-schedules/assigned')

// 근무제 변경요청 화면에서 선택 가능한 활성 근무제 목록. 전체 근무제 목록 조회 권한이 없는 사용자도 호출 가능하다.
export const getWorkScheduleOptions = () =>
  client.get<ApiResponse<WorkSchedule[]>>('/work-schedules/options')

export const assignWorkScheduleToUser = (userId: number, workScheduleId: number) =>
  client.put<ApiResponse<void>>(`/work-schedules/users/${userId}`, { workScheduleId })
