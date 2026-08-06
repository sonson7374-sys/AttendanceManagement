package com.attendance.attendance.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
public class CheckInRequest {

    @NotNull(message = "근무지를 선택해주세요.")
    private Long workplaceId;

    @NotNull(message = "위도를 입력해주세요.")
    @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
    private BigDecimal latitude;

    @NotNull(message = "경도를 입력해주세요.")
    @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
    private BigDecimal longitude;

    @NotNull(message = "위치 정확도를 입력해주세요.")
    @DecimalMin(value = "0.0")
    private BigDecimal accuracyMeters;

    @NotNull(message = "GPS 측정 시각을 입력해주세요.")
    private OffsetDateTime capturedAt;

    private String deviceId;
    private String devicePlatform;
    private boolean mockLocationDetected;
}
