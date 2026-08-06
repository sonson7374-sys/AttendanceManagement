import client from './client'
import type { ApiResponse, BulkHolidayResult, Holiday, HolidayPreset, HolidayType } from '@/types'

export interface HolidayPayload {
  holidayDate: string
  name: string
  holidayType: HolidayType
}

export const getHolidays = () =>
  client.get<ApiResponse<Holiday[]>>('/admin/holidays')

export const getHolidayPresets = (year: number) =>
  client.get<ApiResponse<HolidayPreset[]>>(`/admin/holidays/presets/${year}`)

export const createHoliday = (payload: HolidayPayload) =>
  client.post<ApiResponse<Holiday>>('/admin/holidays', payload)

export const updateHoliday = (id: number, payload: HolidayPayload) =>
  client.put<ApiResponse<Holiday>>(`/admin/holidays/${id}`, payload)

export const bulkCreateHolidays = (payload: HolidayPayload[]) =>
  client.post<ApiResponse<BulkHolidayResult>>('/admin/holidays/bulk', payload)

export const deleteHoliday = (id: number) =>
  client.delete<ApiResponse<void>>(`/admin/holidays/${id}`)
