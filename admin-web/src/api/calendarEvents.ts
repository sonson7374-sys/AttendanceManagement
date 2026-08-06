import client from './client'
import type { ApiResponse, CalendarEvent, CalendarEventCategory, CalendarEventVisibility } from '@/types'

export interface CalendarEventPayload {
  title: string
  startAt: string
  endAt: string
  allDay: boolean
  description?: string
  location?: string
  color?: string
  category: CalendarEventCategory
  visibility: CalendarEventVisibility
}

// 조회는 인증만 되면 누구나 가능(전체 일정 + 본인 개인 일정). 등록/수정/삭제는 서버에서
// 권한레벨(SYSADMIN/HRADMIN/PRESIDENT)로 검증한다.
export const getCalendarEvents = (from: string, to: string) =>
  client.get<ApiResponse<CalendarEvent[]>>('/calendar-events', { params: { from, to } })

export const createCalendarEvent = (payload: CalendarEventPayload) =>
  client.post<ApiResponse<CalendarEvent>>('/calendar-events', payload)

export const updateCalendarEvent = (id: number, payload: CalendarEventPayload) =>
  client.put<ApiResponse<CalendarEvent>>(`/calendar-events/${id}`, payload)

export const deleteCalendarEvent = (id: number) =>
  client.delete<ApiResponse<void>>(`/calendar-events/${id}`)
