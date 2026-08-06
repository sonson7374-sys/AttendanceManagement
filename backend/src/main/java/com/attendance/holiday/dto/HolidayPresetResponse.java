package com.attendance.holiday.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 매년 날짜가 고정된 법정공휴일(신정·삼일절 등) 후보를 년도만 넣으면 계산해 보여주는 응답.
 * 설날·추석·부처님오신날처럼 음력 기준이라 해마다 날짜가 바뀌는 공휴일은 계산할 수 없으므로
 * 포함하지 않는다 — 그런 날짜는 관리자가 직접 등록해야 한다.
 */
@Getter
@Builder
public class HolidayPresetResponse {
    private LocalDate holidayDate;
    private String name;
}
