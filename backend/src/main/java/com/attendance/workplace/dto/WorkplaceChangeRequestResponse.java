package com.attendance.workplace.dto;

import com.attendance.workplace.domain.WorkplaceChangeRequest;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
public class WorkplaceChangeRequestResponse {
    private Long id;
    private Long requesterId;
    private String requesterName;
    private Long currentWorkplaceId;
    private String currentWorkplaceName;
    private String name;
    private String address;
    private String detailAddress;
    private String type;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer radiusMeters;
    private Integer maxAccuracyMeters;
    private boolean checkInAllowed;
    private boolean checkOutAllowed;
    private LocalDate effectiveDate;
    private String reason;
    private String status;
    private String approverName;
    private Long resultingWorkplaceId;
    private Instant createdAt;

    public static WorkplaceChangeRequestResponse from(WorkplaceChangeRequest r, String requesterName,
                                                        String currentWorkplaceName, String approverName) {
        return WorkplaceChangeRequestResponse.builder()
                .id(r.getId())
                .requesterId(r.getRequesterId())
                .requesterName(requesterName)
                .currentWorkplaceId(r.getCurrentWorkplaceId())
                .currentWorkplaceName(currentWorkplaceName)
                .name(r.getName())
                .address(r.getAddress())
                .detailAddress(r.getDetailAddress())
                .type(r.getType())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .radiusMeters(r.getRadiusMeters())
                .maxAccuracyMeters(r.getMaxAccuracyMeters())
                .checkInAllowed(r.isCheckInAllowed())
                .checkOutAllowed(r.isCheckOutAllowed())
                .effectiveDate(r.getEffectiveDate())
                .reason(r.getReason())
                .status(r.getStatus().name())
                .approverName(approverName)
                .resultingWorkplaceId(r.getResultingWorkplaceId())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
