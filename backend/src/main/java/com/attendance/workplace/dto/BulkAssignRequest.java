package com.attendance.workplace.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
public class BulkAssignRequest {

    @NotEmpty(message = "배정할 직원을 선택해주세요.")
    private List<Long> userIds;

    private LocalDate validFrom;
    private LocalDate validTo;
}
