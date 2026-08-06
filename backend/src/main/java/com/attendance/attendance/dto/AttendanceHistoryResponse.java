package com.attendance.attendance.dto;

import com.attendance.attendance.domain.AttendanceRecord;
import com.attendance.attendance.domain.AttendanceStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
public class AttendanceHistoryResponse {

    private Long attendanceId;
    private LocalDate workDate;
    private AttendanceStatus status;
    private Instant checkInAt;
    private Instant checkOutAt;
    private String workplaceName;
    private boolean late;
    private boolean earlyLeave;
    private Integer workMinutes;
    private Integer breakMinutes;
    private Integer overtimeMinutes;
    private Integer nightMinutes;

    public static AttendanceHistoryResponse from(AttendanceRecord record, String workplaceName, Integer nightMinutes,
                                                  boolean late, boolean earlyLeave) {
        return AttendanceHistoryResponse.builder()
                .attendanceId(record.getId())
                .workDate(record.getWorkDate())
                .status(record.getStatus())
                .checkInAt(record.getCheckInAt())
                .checkOutAt(record.getCheckOutAt())
                .workplaceName(workplaceName)
                .late(late)
                .earlyLeave(earlyLeave)
                .workMinutes(record.getWorkMinutes())
                .breakMinutes(record.getBreakMinutes())
                .overtimeMinutes(record.getOvertimeMinutes())
                .nightMinutes(nightMinutes)
                .build();
    }
}
