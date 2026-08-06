package com.attendance.outsidework.dto;

import com.attendance.attendance.domain.ChangeRequestStatus;
import com.attendance.outsidework.domain.OutsideWorkRequest;
import com.attendance.outsidework.domain.OutsideWorkRequestType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class OutsideWorkRequestResponse {

    private Long id;
    private Long requesterId;
    private String requesterName;
    private OutsideWorkRequestType requestType;
    private Instant startAt;
    private Instant endAt;
    private String reason;
    private ChangeRequestStatus status;
    private String approverName;
    private String destinationAddress;
    private BigDecimal destinationLatitude;
    private BigDecimal destinationLongitude;
    private Integer tempRadiusMeters;
    private String visitPurpose;
    private String clientName;
    private Instant expectedReturnAt;
    private Instant createdAt;

    public static OutsideWorkRequestResponse from(OutsideWorkRequest req) {
        return from(req, null, null);
    }

    public static OutsideWorkRequestResponse from(OutsideWorkRequest req, String requesterName, String approverName) {
        return OutsideWorkRequestResponse.builder()
                .id(req.getId())
                .requesterId(req.getRequesterId())
                .requesterName(requesterName)
                .requestType(req.getRequestType())
                .startAt(req.getStartAt())
                .endAt(req.getEndAt())
                .reason(req.getReason())
                .status(req.getStatus())
                .approverName(approverName)
                .destinationAddress(req.getDestinationAddress())
                .destinationLatitude(req.getDestinationLatitude())
                .destinationLongitude(req.getDestinationLongitude())
                .tempRadiusMeters(req.getTempRadiusMeters())
                .visitPurpose(req.getVisitPurpose())
                .clientName(req.getClientName())
                .expectedReturnAt(req.getExpectedReturnAt())
                .createdAt(req.getCreatedAt())
                .build();
    }
}
