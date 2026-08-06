package com.attendance.admin.dto;

import com.attendance.attendance.domain.AttendanceRecord;
import com.attendance.attendance.domain.AttendanceStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
public class AdminAttendanceResponse {
    private Long attendanceId;
    private Long userId;
    private String userName;
    private String employeeNumber;
    private LocalDate workDate;
    private AttendanceStatus status;
    private Instant checkInAt;
    private Instant checkOutAt;
    private String workplaceName;
    private boolean late;
    private boolean earlyLeave;
    private boolean closed;
    private Integer workMinutes;
    private Integer breakMinutes;
    private Integer overtimeMinutes;
    private Integer checkInDistanceMeters;
    private Integer checkOutDistanceMeters;
    private BigDecimal checkInAccuracyMeters;
    private String processMethod;

    public static AdminAttendanceResponse from(AttendanceRecord record, String userName,
                                                String employeeNumber, String workplaceName,
                                                boolean late, boolean earlyLeave) {
        return AdminAttendanceResponse.builder()
                .attendanceId(record.getId())
                .userId(record.getUserId())
                .userName(userName)
                .employeeNumber(employeeNumber)
                .workDate(record.getWorkDate())
                .status(record.getStatus())
                .checkInAt(record.getCheckInAt())
                .checkOutAt(record.getCheckOutAt())
                .workplaceName(workplaceName)
                .late(late)
                .earlyLeave(earlyLeave)
                .closed(record.isClosed())
                .workMinutes(record.getWorkMinutes())
                .breakMinutes(record.getBreakMinutes())
                .overtimeMinutes(record.getOvertimeMinutes())
                .checkInDistanceMeters(record.getCheckInDistanceMeters())
                .checkOutDistanceMeters(record.getCheckOutDistanceMeters())
                .checkInAccuracyMeters(record.getCheckInAccuracyMeters())
                .processMethod(record.getCheckInLatitude() != null ? "GPS" : "MANUAL")
                .build();
    }
}
