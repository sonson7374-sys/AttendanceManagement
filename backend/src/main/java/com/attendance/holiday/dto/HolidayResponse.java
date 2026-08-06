package com.attendance.holiday.dto;

import com.attendance.holiday.domain.Holiday;
import com.attendance.holiday.domain.HolidayType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class HolidayResponse {
    private Long id;
    private LocalDate holidayDate;
    private String name;
    private HolidayType holidayType;

    public static HolidayResponse from(Holiday holiday) {
        return HolidayResponse.builder()
                .id(holiday.getId())
                .holidayDate(holiday.getHolidayDate())
                .name(holiday.getName())
                .holidayType(holiday.getHolidayType())
                .build();
    }
}
