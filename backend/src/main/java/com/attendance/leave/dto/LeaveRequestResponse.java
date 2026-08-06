package com.attendance.leave.dto;

import com.attendance.attendance.domain.ChangeRequestStatus;
import com.attendance.leave.domain.LeaveRequest;
import com.attendance.leave.domain.LeaveRequestType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class LeaveRequestResponse {

    private Long id;
    private Long requesterId;
    private String employeeNumber;
    private String requesterName;
    private LeaveRequestType requestType;
    private Instant startAt;
    private Instant endAt;
    private String reason;
    private ChangeRequestStatus status;
    private String approverName;
    private Instant createdAt;

    public static LeaveRequestResponse from(LeaveRequest req) {
        return from(req, null, null);
    }

    public static LeaveRequestResponse from(LeaveRequest req, String requesterName, String approverName) {
        return LeaveRequestResponse.builder()
                .id(req.getId())
                .requesterId(req.getRequesterId())
                .employeeNumber(req.getEmployeeNumber())
                .requesterName(requesterName)
                .requestType(req.getRequestType())
                .startAt(req.getStartAt())
                .endAt(req.getEndAt())
                .reason(req.getReason())
                .status(req.getStatus())
                .approverName(approverName)
                .createdAt(req.getCreatedAt())
                .build();
    }
}
