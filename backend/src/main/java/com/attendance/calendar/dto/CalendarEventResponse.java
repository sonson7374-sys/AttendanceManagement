package com.attendance.calendar.dto;

import com.attendance.calendar.domain.CalendarEvent;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class CalendarEventResponse {
    private Long id;
    private String title;
    private Instant startAt;
    private Instant endAt;
    private boolean allDay;
    private String description;
    private String location;
    private String color;
    private String category;
    private String visibility;
    private Long targetUserId;
    private String targetUserName;
    private Long createdBy;
    private String createdByName;
    private Instant createdAt;
    private Instant updatedAt;

    public static CalendarEventResponse from(CalendarEvent e, String targetUserName, String createdByName) {
        return CalendarEventResponse.builder()
                .id(e.getId())
                .title(e.getTitle())
                .startAt(e.getStartAt())
                .endAt(e.getEndAt())
                .allDay(e.isAllDay())
                .description(e.getDescription())
                .location(e.getLocation())
                .color(e.getColor())
                .category(e.getCategory().name())
                .visibility(e.getVisibility().name())
                .targetUserId(e.getTargetUserId())
                .targetUserName(targetUserName)
                .createdBy(e.getCreatedBy())
                .createdByName(createdByName)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
