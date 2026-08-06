package com.attendance.calendar.dto;

import com.attendance.calendar.domain.CalendarEventCategory;
import com.attendance.calendar.domain.CalendarEventVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarEventCreateRequest {

    @NotBlank(message = "제목을 입력해주세요.")
    private String title;

    @NotNull(message = "시작 일시를 입력해주세요.")
    private OffsetDateTime startAt;

    @NotNull(message = "종료 일시를 입력해주세요.")
    private OffsetDateTime endAt;

    private boolean allDay;
    private String description;
    private String location;
    private String color;

    @NotNull(message = "구분을 선택해주세요.")
    private CalendarEventCategory category;

    @NotNull(message = "공개 범위를 선택해주세요.")
    private CalendarEventVisibility visibility;
}
