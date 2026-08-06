package com.attendance.attendance.dto;

import com.attendance.attendance.domain.AttendanceRecord;
import com.attendance.attendance.domain.AttendanceStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class AttendanceResponse {
    private Long attendanceId;
    private AttendanceStatus status;
    private Instant checkInAt;
    private Instant checkOutAt;
    private String workplaceName;
    private double distanceMeters;
    private boolean withinGeofence;
    private boolean late;
    private boolean earlyLeave;
    private Integer workMinutes;
    private Integer breakMinutes;
    private Integer overtimeMinutes;

    public static AttendanceResponse fromCheckIn(AttendanceRecord record,
                                                  String workplaceName,
                                                  double distanceMeters) {
        return AttendanceResponse.builder()
                .attendanceId(record.getId())
                .status(record.getStatus())
                .checkInAt(record.getCheckInAt())
                .workplaceName(workplaceName)
                .distanceMeters(distanceMeters)
                .withinGeofence(true)
                .late(record.isLate())
                .build();
    }

    public static AttendanceResponse fromBreak(AttendanceRecord record, String workplaceName) {
        return AttendanceResponse.builder()
                .attendanceId(record.getId())
                .status(record.getStatus())
                .checkInAt(record.getCheckInAt())
                .workplaceName(workplaceName)
                .late(record.isLate())
                .workMinutes(record.getWorkMinutes())
                .breakMinutes(record.getBreakMinutes())
                .overtimeMinutes(record.getOvertimeMinutes())
                .build();
    }

    public static AttendanceResponse fromCheckOut(AttendanceRecord record,
                                                   String workplaceName,
                                                   double distanceMeters) {
        return AttendanceResponse.builder()
                .attendanceId(record.getId())
                .status(record.getStatus())
                .checkInAt(record.getCheckInAt())
                .checkOutAt(record.getCheckOutAt())
                .workplaceName(workplaceName)
                .distanceMeters(distanceMeters)
                .withinGeofence(true)
                .late(record.isLate())
                .earlyLeave(record.isEarlyLeave())
                .workMinutes(record.getWorkMinutes())
                .breakMinutes(record.getBreakMinutes())
                .overtimeMinutes(record.getOvertimeMinutes())
                .build();
    }
}
