export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
  timestamp: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type UserRole = 'EMPLOYEE' | 'MANAGER' | 'HR_ADMIN' | 'SYSTEM_ADMIN'
export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'LOCKED'

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  userId: number
  email: string
  name: string
  role: UserRole
  level: string
}

export interface User {
  id: number
  email: string
  name: string
  employeeNumber: string
  role: UserRole
  level: string
  status: UserStatus
  companyId: number
  organizationId?: number
  phone?: string
  jobTitle?: string
  employmentType?: string
  hireDate?: string
  resignDate?: string
  defaultWorkplaceId?: number
  workScheduleId?: number
  createdAt: string
}

export type WorkplaceType = 'OFFICE' | 'LARGE_SITE' | 'CONSTRUCTION_SITE' | 'INDOOR' | 'OTHER'

export interface Workplace {
  id: number
  companyId: number
  name: string
  address: string
  detailAddress?: string
  type: WorkplaceType
  latitude: number
  longitude: number
  radiusMeters: number
  maxAccuracyMeters?: number
  checkInAllowed: boolean
  checkOutAllowed: boolean
  validFrom?: string
  validTo?: string
  active: boolean
}

export interface UserDevice {
  id: number
  deviceId: string
  devicePlatform?: string
  deviceName?: string
  active: boolean
  registeredAt: string
  lastSeenAt?: string
}

export type AttendanceStatus =
  | 'BEFORE_WORK' | 'WORKING' | 'BREAK' | 'FINISHED' | 'LATE'
  | 'EARLY_LEAVE' | 'ABSENT' | 'LEAVE' | 'OUTSIDE_WORK'
  | 'BUSINESS_TRIP' | 'REMOTE_WORK'

export interface AttendanceRecord {
  attendanceId: number
  userId: number
  userName: string
  employeeNumber: string
  workDate: string
  status: AttendanceStatus
  checkInAt?: string
  checkOutAt?: string
  workplaceName?: string
  late: boolean
  earlyLeave: boolean
  closed: boolean
  workMinutes?: number
  breakMinutes?: number
  overtimeMinutes?: number
  checkInDistanceMeters?: number
  checkOutDistanceMeters?: number
  checkInAccuracyMeters?: number
  processMethod?: string
}

export interface AttendanceScopeInfo {
  employeeLevel: boolean
  hasSubordinates: boolean
  organizationIds?: number[]
  workplaceIds?: number[]
}

export interface AttendanceBoardRow {
  userId: number
  userName: string
  employeeNumber: string
  organizationId?: number
  attendanceId?: number
  status?: AttendanceStatus
  checkInAt?: string
  checkOutAt?: string
  scheduleStartTime?: string
  scheduleEndTime?: string
  workMinutes?: number
  breakMinutes?: number
  overtimeMinutes?: number
  late: boolean
  earlyLeave: boolean
  closed: boolean
  hasPendingChangeRequest: boolean
  leaveTypeLabel?: string
}

export type ChangeRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELED'
export type ChangeRequestType =
  | 'CHECK_IN_TIME' | 'CHECK_OUT_TIME' | 'ABSENT_CORRECTION'
  | 'WORKPLACE_CHANGE' | 'LATE_CORRECTION'

export interface ChangeRequest {
  id: number
  requesterId: number
  requesterName?: string
  recordId?: number
  targetDate: string
  changeType: ChangeRequestType
  requestedCheckIn?: string
  requestedCheckOut?: string
  requestedWorkplaceId?: number
  reason: string
  status: ChangeRequestStatus
  approverName?: string
  createdAt: string
}

export type LeaveRequestType = 'ANNUAL' | 'HALF_DAY' | 'HOURLY' | 'SICK' | 'OFFICIAL' | 'OVERTIME' | 'HOLIDAY_WORK' | 'ZERO_DAY' | 'EARLY'

export interface LeaveRequest {
  id: number
  requesterId: number
  requesterName?: string
  requestType: LeaveRequestType
  startAt: string
  endAt: string
  reason: string
  status: ChangeRequestStatus
  approverName?: string
  createdAt: string
}

export type OutsideWorkRequestType = 'OUTSIDE_WORK' | 'BUSINESS_TRIP' | 'REMOTE_WORK'

export interface OutsideWorkRequest {
  id: number
  requesterId: number
  requesterName?: string
  requestType: OutsideWorkRequestType
  startAt: string
  endAt: string
  reason: string
  status: ChangeRequestStatus
  approverName?: string
  destinationAddress?: string
  destinationLatitude?: number
  destinationLongitude?: number
  tempRadiusMeters?: number
  visitPurpose?: string
  clientName?: string
  expectedReturnAt?: string
  createdAt: string
}

export type CalendarEventCategory = 'MEETING' | 'EVENT' | 'NOTICE' | 'ETC'
export type CalendarEventVisibility = 'ALL' | 'PERSONAL'

export interface CalendarEvent {
  id: number
  title: string
  startAt: string
  endAt: string
  allDay: boolean
  description?: string
  location?: string
  color?: string
  category: CalendarEventCategory
  visibility: CalendarEventVisibility
  targetUserId?: number
  targetUserName?: string
  createdBy: number
  createdByName?: string
  createdAt: string
  updatedAt: string
}

export interface WorkplaceChangeRequest {
  id: number
  requesterId: number
  requesterName?: string
  currentWorkplaceId?: number
  currentWorkplaceName?: string
  name: string
  address?: string
  detailAddress?: string
  type: string
  latitude: number
  longitude: number
  radiusMeters: number
  maxAccuracyMeters?: number
  checkInAllowed: boolean
  checkOutAllowed: boolean
  effectiveDate: string
  reason: string
  status: ChangeRequestStatus
  approverName?: string
  resultingWorkplaceId?: number
  createdAt: string
}

export interface WorkScheduleChangeRequest {
  id: number
  requesterId: number
  requesterName?: string
  currentWorkScheduleId?: number
  currentWorkScheduleName?: string
  targetWorkScheduleId: number
  targetWorkScheduleName?: string
  effectiveMonth: string
  reason: string
  status: ChangeRequestStatus
  approverName?: string
  createdAt: string
}

export type ApprovalQueueKind = 'CHANGE_REQUEST' | 'LEAVE_REQUEST' | 'OUTSIDE_WORK_REQUEST' | 'WORKPLACE_CHANGE_REQUEST' | 'WORK_SCHEDULE_CHANGE_REQUEST'

export interface ApprovalQueueItem {
  kind: ApprovalQueueKind
  id: number
  createdAt: string
  changeRequest?: ChangeRequest
  leaveRequest?: LeaveRequest
  outsideWorkRequest?: OutsideWorkRequest
  workplaceChangeRequest?: WorkplaceChangeRequest
  workScheduleChangeRequest?: WorkScheduleChangeRequest
}

export interface DepartmentAttendanceRate {
  organizationId: number
  organizationName: string
  presentCount: number
  totalCount: number
  rate: number
}

export interface MonthlyLateTrendPoint {
  yearMonth: string
  lateCount: number
}

export interface DashboardStats {
  totalEmployees: number
  presentToday: number
  lateToday: number
  absentToday: number
  onLeaveToday: number
  outsideWorkToday: number
  checkedOutToday: number
  pendingApprovals: number
  departmentAttendanceRates: DepartmentAttendanceRate[]
  monthlyLateTrend: MonthlyLateTrendPoint[]
}

export interface MonthlyUserSummary {
  userId: number
  userName: string
  employeeNumber: string
  workingDays: number
  presentDays: number
  lateDays: number
  earlyLeaveDays: number
  absentDays: number
  totalWorkMinutes: number
  totalOvertimeMinutes: number
}

export interface AuditLog {
  id: number
  actorId?: number
  actorEmail?: string
  action: string
  targetType?: string
  targetId?: number
  detail?: Record<string, unknown>
  ipAddress?: string
  createdAt: string
}

export interface Organization {
  id: number
  companyId: number
  parentId?: number
  name: string
  displayOrder?: number
  active: boolean
  createdAt: string
}

export type HolidayType = 'PUBLIC' | 'SUBSTITUTE' | 'COMPANY' | 'WEEKEND'

export interface Holiday {
  id: number
  holidayDate: string
  name: string
  holidayType: HolidayType
}

export interface HolidayPreset {
  holidayDate: string
  name: string
}

export interface BulkHolidayResult {
  created: number
  skipped: number
}

export type WorkScheduleType = 'FIXED' | 'FLEXTIME' | 'SELECTIVE' | 'ELASTIC' | 'SHIFT' | 'REMOTE'

export interface WorkSchedule {
  id: number
  companyId: number
  name: string
  workStartTime: string
  workEndTime: string
  requiredWorkMinutes: number
  overtimeThresholdMin: number
  defaultSchedule: boolean
  active: boolean
  scheduleType: WorkScheduleType
  lateThresholdMinutes: number
  earlyLeaveThresholdMinutes: number
  breakMinutes: number
  nightShiftStart?: string
  nightShiftEnd?: string
  holidayWorkThresholdMinutes: number
}
