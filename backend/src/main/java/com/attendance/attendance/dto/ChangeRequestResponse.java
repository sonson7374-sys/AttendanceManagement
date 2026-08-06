package com.attendance.attendance.dto;

import com.attendance.attendance.domain.AttendanceChangeRequest;
import com.attendance.attendance.domain.ChangeRequestStatus;
import com.attendance.attendance.domain.ChangeRequestType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
public class ChangeRequestResponse {

    private Long id;
    private Long requesterId;
    private String requesterName;
    private Long recordId;
    private LocalDate targetDate;
    private ChangeRequestType changeType;
    private Instant requestedCheckIn;
    private Instant requestedCheckOut;
    private Long requestedWorkplaceId;
    private String reason;
    private ChangeRequestStatus status;
    private String approverName;
    private Instant createdAt;

    public static ChangeRequestResponse from(AttendanceChangeRequest req) {
        return from(req, null, null);
    }

    public static ChangeRequestResponse from(AttendanceChangeRequest req, String requesterName, String approverName) {
        return ChangeRequestResponse.builder()
                .id(req.getId())
                .requesterId(req.getRequesterId())
                .requesterName(requesterName)
                .recordId(req.getRecordId())
                .targetDate(req.getTargetDate())
                .changeType(req.getChangeType())
                .requestedCheckIn(req.getRequestedCheckIn())
                .requestedCheckOut(req.getRequestedCheckOut())
                .requestedWorkplaceId(req.getRequestedWorkplaceId())
                .reason(req.getReason())
                .status(req.getStatus())
                .approverName(approverName)
                .createdAt(req.getCreatedAt())
                .build();
    }
}
