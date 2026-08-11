package com.attendance.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DepartmentAttendanceRate {
    private Long organizationId;
    private String organizationName;
    private String parentOrganizationName;
    private long presentCount;
    private long totalCount;
    private double rate;
}
