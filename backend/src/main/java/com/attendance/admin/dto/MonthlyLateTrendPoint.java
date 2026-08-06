package com.attendance.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MonthlyLateTrendPoint {
    private String yearMonth;
    private long lateCount;
}
