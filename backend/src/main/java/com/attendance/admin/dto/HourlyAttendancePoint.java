package com.attendance.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HourlyAttendancePoint {
    private String hour;
    private long checkInCount;
    private long checkOutCount;
}
