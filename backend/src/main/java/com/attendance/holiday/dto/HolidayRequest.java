package com.attendance.holiday.dto;

import com.attendance.holiday.domain.HolidayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class HolidayRequest {

    @NotNull
    private LocalDate holidayDate;

    @NotBlank
    private String name;

    private HolidayType holidayType;
}
