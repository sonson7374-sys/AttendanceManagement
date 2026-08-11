package com.attendance.admin.dto;

import com.attendance.attendance.domain.AttendanceRecord;
import com.attendance.attendance.domain.AttendanceStatus;
import com.attendance.schedule.domain.WorkSchedule;
import com.attendance.user.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalTime;

/**
 * 출근부(지정일) 화면 - 하루 기준으로 여러 직원의 근태를 한 행씩 보여주기 위한 조회 전용 DTO.
 * attendanceId가 없으면 해당 날짜에 아직 근태 기록이 없다는 뜻이며, 이 화면에서는 보정만 지원하므로
 * (수동 등록은 별도 화면·권한) attendanceId가 있는 행만 출근/퇴근 시각을 수정할 수 있다.
 */
@Getter
@Builder
public class AdminAttendanceBoardRow {
    private Long userId;
    private String userName;
    private String employeeNumber;
    private Long organizationId;
    private Long attendanceId;
    private AttendanceStatus status;
    private Instant checkInAt;
    private Instant checkOutAt;
    /** 근태 기록에 이미 저장된 근무지가 있으면 그 값, 없으면 이 직원에게 배정된 근무지(있는 경우). */
    private Long workplaceId;
    private String workplaceName;
    private LocalTime scheduleStartTime;
    private LocalTime scheduleEndTime;
    private Integer workMinutes;
    private Integer breakMinutes;
    private Integer overtimeMinutes;
    private boolean late;
    private boolean earlyLeave;
    private boolean closed;
    private boolean hasPendingChangeRequest;
    private String leaveTypeLabel;

    public static AdminAttendanceBoardRow of(User user, AttendanceRecord record, WorkSchedule schedule,
                                              boolean late, boolean earlyLeave, boolean hasPendingChangeRequest,
                                              String leaveTypeLabel, Long workplaceId, String workplaceName) {
        // 일괄등록(엑셀)으로 승인된 휴가는 근태 기록에 자동 반영되지 않아 record가 없거나 상태가 낡을 수 있으므로,
        // 그날 승인된 휴가가 확인되면(leaveTypeLabel != null) 근태 기록 유무와 상관없이 근태상황을 휴가로 표시한다.
        AttendanceStatus status = leaveTypeLabel != null ? AttendanceStatus.LEAVE
                : (record != null ? record.getStatus() : null);
        return AdminAttendanceBoardRow.builder()
                .userId(user.getId())
                .userName(user.getName())
                .employeeNumber(user.getEmployeeNumber())
                .organizationId(user.getOrganizationId())
                .attendanceId(record != null ? record.getId() : null)
                .status(status)
                .checkInAt(record != null ? record.getCheckInAt() : null)
                .checkOutAt(record != null ? record.getCheckOutAt() : null)
                .workplaceId(workplaceId)
                .workplaceName(workplaceName)
                .scheduleStartTime(schedule != null ? schedule.getWorkStartTime() : null)
                .scheduleEndTime(schedule != null ? schedule.getWorkEndTime() : null)
                .workMinutes(record != null ? record.getWorkMinutes() : null)
                .breakMinutes(record != null ? record.getBreakMinutes() : null)
                .overtimeMinutes(record != null ? record.getOvertimeMinutes() : null)
                .late(late)
                .earlyLeave(earlyLeave)
                .closed(record != null && record.isClosed())
                .hasPendingChangeRequest(hasPendingChangeRequest)
                .leaveTypeLabel(leaveTypeLabel)
                .build();
    }
}
