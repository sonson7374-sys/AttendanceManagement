import client from './client'
import type { ApiResponse, AttendanceStatus, Workplace } from '@/types'

// 로그인한 본인의 출퇴근 체크/출근부. 모바일 앱과 동일한 백엔드 엔드포인트를 그대로 사용한다.
// 관리자웹(WEB)에서는 브라우저 GPS를 측위하지 않고, 계정에 배정된 근무지의 위도/경도/정확도를
// 그대로 좌표로 보낸다(MyAttendancePage.tsx의 CheckInOutTab 참고) — 물리적 위치와 무관하게 처리된다.

export interface MyTodayAttendance {
  attendanceId: number
  workDate: string
  status: AttendanceStatus
  checkInAt?: string
  checkOutAt?: string
  workplaceName?: string
  late: boolean
  earlyLeave: boolean
  workMinutes?: number
  checkInDistanceMeters?: number
  checkOutDistanceMeters?: number
}

export interface CheckInOutPayload {
  workplaceId?: number
  latitude: number
  longitude: number
  accuracyMeters: number
  capturedAt: string
  deviceId: string
  devicePlatform: string
  mockLocationDetected: boolean
}

export interface AttendanceRegisterRow {
  workDate: string
  holidayLabel?: string
  scheduleStartTime?: string
  scheduleEndTime?: string
  checkInAt?: string
  checkOutAt?: string
  workMinutes?: number
  outsideScheduleMinutes?: number
  overtimeMinutes?: number
  nightMinutes?: number
  breakMinutes?: number
  status?: AttendanceStatus
  late: boolean
  earlyLeave: boolean
}

export const getMyTodayAttendance = () =>
  client.get<ApiResponse<MyTodayAttendance>>('/attendance/today')

export const checkInMyAttendance = (payload: CheckInOutPayload) =>
  client.post<ApiResponse<MyTodayAttendance>>('/attendance/check-in', payload, {
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  })

export const checkOutMyAttendance = (payload: Omit<CheckInOutPayload, 'workplaceId'>) =>
  client.post<ApiResponse<MyTodayAttendance>>('/attendance/check-out', payload, {
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  })

export const getMyAssignedWorkplaces = () =>
  client.get<ApiResponse<Workplace[]>>('/workplaces/assigned')

export const getMyAttendanceRegister = (from: string, to: string) =>
  client.get<ApiResponse<AttendanceRegisterRow[]>>('/attendance/register', { params: { from, to } })

export interface MyAttendanceHistoryRow {
  attendanceId: number
  workDate: string
  status: AttendanceStatus
  checkInAt?: string
  checkOutAt?: string
  workplaceName?: string
  late: boolean
  earlyLeave: boolean
  workMinutes?: number
  breakMinutes?: number
}

// 근태 수정 요청 화면에서 "대상 근태 기록" 선택용. 모바일 앱과 동일하게 최근 근태 이력에서 골라 recordId로 신청한다.
export const getMyAttendanceHistory = (from: string, to: string) =>
  client.get<ApiResponse<MyAttendanceHistoryRow[]>>('/attendance', { params: { from, to } })
