package com.attendance.outsidework.dto;

import com.attendance.outsidework.domain.OutsideWorkRequestType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
public class OutsideWorkRequestSubmitRequest {

    @NotNull
    private OutsideWorkRequestType requestType;

    @NotNull
    private OffsetDateTime startAt;

    @NotNull
    private OffsetDateTime endAt;

    @NotNull
    private String reason;

    private String destinationAddress;
    private BigDecimal destinationLatitude;
    private BigDecimal destinationLongitude;
    private Integer tempRadiusMeters;
    private String visitPurpose;
    private String clientName;
    private OffsetDateTime expectedReturnAt;
}
