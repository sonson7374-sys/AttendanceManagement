package com.attendance.attendance.dto;

import com.attendance.attendance.domain.AttendanceRecord;
import com.attendance.attendance.domain.AttendanceStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
public class TodayAttendanceResponse {
    private Long attendanceId;
    private LocalDate workDate;
    private AttendanceStatus status;
    private Instant checkInAt;
    private Instant checkOutAt;
    private String workplaceName;
    private boolean late;
    private boolean earlyLeave;
    private Integer workMinutes;
    private Integer checkInDistanceMeters;
    private Integer checkOutDistanceMeters;
    private BigDecimal checkInAccuracyMeters;

    public static TodayAttendanceResponse absent(LocalDate date) {
        return TodayAttendanceResponse.builder()
                .workDate(date)
                .status(AttendanceStatus.BEFORE_WORK)
                .build();
    }

    public static TodayAttendanceResponse from(AttendanceRecord record, String workplaceName,
                                                boolean late, boolean earlyLeave) {
        return TodayAttendanceResponse.builder()
                .attendanceId(record.getId())
                .workDate(record.getWorkDate())
                .status(record.getStatus())
                .checkInAt(record.getCheckInAt())
                .checkOutAt(record.getCheckOutAt())
                .workplaceName(workplaceName)
                .late(late)
                .earlyLeave(earlyLeave)
                .workMinutes(record.getWorkMinutes())
                .checkInDistanceMeters(record.getCheckInDistanceMeters())
                .checkOutDistanceMeters(record.getCheckOutDistanceMeters())
                .checkInAccuracyMeters(record.getCheckInAccuracyMeters())
                .build();
    }
}
