package com.attendance.workplace.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class WorkplaceChangeRequestSubmitRequest {

    /** 신청 시점에 신청자가 배정되어 있던 근무지(있는 경우). 승인 시 이 배정이 해제된다. */
    private Long currentWorkplaceId;

    @NotBlank(message = "근무지명을 입력해주세요.")
    private String name;

    private String address;
    private String detailAddress;
    private String type = "OFFICE";

    @NotNull(message = "위도를 입력해주세요.")
    @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
    @DecimalMax(value = "90.0", message = "위도는 90 이하이어야 합니다.")
    private BigDecimal latitude;

    @NotNull(message = "경도를 입력해주세요.")
    @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
    @DecimalMax(value = "180.0", message = "경도는 180 이하이어야 합니다.")
    private BigDecimal longitude;

    @Min(value = 1, message = "허용 반경은 1m 이상이어야 합니다.")
    private Integer radiusMeters = 100;

    private Integer maxAccuracyMeters;
    private boolean checkInAllowed = true;
    private boolean checkOutAllowed = true;

    @NotNull(message = "적용 예정일을 선택해주세요.")
    private LocalDate effectiveDate;

    @NotBlank(message = "사유를 입력해주세요.")
    private String reason;
}
