package com.attendance.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DashboardStatsResponse {
    private long totalEmployees;
    private long presentToday;
    private long lateToday;
    private long absentToday;
    private long onLeaveToday;
    private long outsideWorkToday;
    private long checkedOutToday;
    private long pendingApprovals;
    private List<DepartmentAttendanceRate> departmentAttendanceRates;
    private List<MonthlyLateTrendPoint> monthlyLateTrend;
}
