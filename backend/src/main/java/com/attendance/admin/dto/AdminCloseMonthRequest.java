package com.attendance.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminCloseMonthRequest {

    @NotNull(message = "연도를 입력해주세요.")
    private Integer year;

    @NotNull(message = "월을 입력해주세요.")
    @Min(1) @Max(12)
    private Integer month;
}
