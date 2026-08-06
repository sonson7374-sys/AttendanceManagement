package com.attendance.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminMonthlyUserSummary {
    private Long userId;
    private String userName;
    private String employeeNumber;
    private int workingDays;   // 해당 월 평일 수 (단순 기록 건수)
    private int presentDays;
    private int lateDays;
    private int earlyLeaveDays;
    private int absentDays;
    private int totalWorkMinutes;
    private int totalOvertimeMinutes;
}
