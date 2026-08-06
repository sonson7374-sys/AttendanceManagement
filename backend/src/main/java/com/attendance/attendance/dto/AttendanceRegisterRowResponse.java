package com.attendance.attendance.dto;

import com.attendance.attendance.domain.AttendanceStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Builder
public class AttendanceRegisterRowResponse {
    private LocalDate workDate;
    private String holidayLabel;
    private LocalTime scheduleStartTime;
    private LocalTime scheduleEndTime;
    private Instant checkInAt;
    private Instant checkOutAt;
    private Integer workMinutes;
    private Integer outsideScheduleMinutes;
    private Integer overtimeMinutes;
    private Integer nightMinutes;
    private Integer breakMinutes;
    private AttendanceStatus status;
    private boolean late;
    private boolean earlyLeave;
}
