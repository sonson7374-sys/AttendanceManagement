package com.attendance.schedule.dto;

import com.attendance.attendance.domain.ChangeRequestStatus;
import com.attendance.schedule.domain.WorkScheduleChangeRequest;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.YearMonth;

@Getter
@Builder
public class WorkScheduleChangeRequestResponse {
    private Long id;
    private Long requesterId;
    private String requesterName;
    private Long currentWorkScheduleId;
    private String currentWorkScheduleName;
    private Long targetWorkScheduleId;
    private String targetWorkScheduleName;
    private YearMonth effectiveMonth;
    private String reason;
    private ChangeRequestStatus status;
    private String approverName;
    private Instant createdAt;

    public static WorkScheduleChangeRequestResponse from(WorkScheduleChangeRequest r, String requesterName,
                                                          String currentWorkScheduleName, String targetWorkScheduleName,
                                                          String approverName) {
        return WorkScheduleChangeRequestResponse.builder()
                .id(r.getId())
                .requesterId(r.getRequesterId())
                .requesterName(requesterName)
                .currentWorkScheduleId(r.getCurrentWorkScheduleId())
                .currentWorkScheduleName(currentWorkScheduleName)
                .targetWorkScheduleId(r.getTargetWorkScheduleId())
                .targetWorkScheduleName(targetWorkScheduleName)
                .effectiveMonth(YearMonth.from(r.getEffectiveMonth()))
                .reason(r.getReason())
                .status(r.getStatus())
                .approverName(approverName)
                .createdAt(r.getCreatedAt())
                .build();
    }
}
