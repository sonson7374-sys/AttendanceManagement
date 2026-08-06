package com.attendance.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.YearMonth;

@Getter
@NoArgsConstructor
public class WorkScheduleChangeRequestSubmitRequest {

    /** 신청 시점에 신청자가 배정되어 있던 근무제(있는 경우). 승인 시 이 배정이 신규 근무제로 교체된다. */
    private Long currentWorkScheduleId;

    @NotNull(message = "변경할 근무제를 선택해주세요.")
    private Long targetWorkScheduleId;

    @NotNull(message = "적용 예정월을 선택해주세요.")
    private YearMonth effectiveMonth;

    @NotBlank(message = "사유를 입력해주세요.")
    private String reason;
}
