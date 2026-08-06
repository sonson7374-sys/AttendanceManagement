package com.attendance.holiday.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BulkHolidayResult {
    private int created;
    private int skipped;
}
