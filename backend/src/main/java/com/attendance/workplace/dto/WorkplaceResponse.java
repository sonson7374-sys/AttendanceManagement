package com.attendance.workplace.dto;

import com.attendance.workplace.domain.Workplace;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class WorkplaceResponse {
    private Long id;
    private Long companyId;
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
    private LocalDate validFrom;
    private LocalDate validTo;
    private boolean active;

    public static WorkplaceResponse from(Workplace wp) {
        return WorkplaceResponse.builder()
                .id(wp.getId())
                .companyId(wp.getCompanyId())
                .name(wp.getName())
                .address(wp.getAddress())
                .detailAddress(wp.getDetailAddress())
                .type(wp.getType())
                .latitude(wp.getLatitude())
                .longitude(wp.getLongitude())
                .radiusMeters(wp.getRadiusMeters())
                .maxAccuracyMeters(wp.getMaxAccuracyMeters())
                .checkInAllowed(wp.isCheckInAllowed())
                .checkOutAllowed(wp.isCheckOutAllowed())
                .validFrom(wp.getValidFrom())
                .validTo(wp.getValidTo())
                .active(wp.isActive())
                .build();
    }
}
