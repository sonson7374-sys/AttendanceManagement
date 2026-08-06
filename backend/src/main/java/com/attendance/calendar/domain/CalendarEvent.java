package com.attendance.calendar.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "calendar_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 200)
    private String location;

    @Column(length = 20)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CalendarEventCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CalendarEventVisibility visibility;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public CalendarEvent(String title, Instant startAt, Instant endAt, boolean allDay, String description,
                          String location, String color, CalendarEventCategory category,
                          CalendarEventVisibility visibility, Long targetUserId, Long createdBy) {
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
        this.description = description;
        this.location = location;
        this.color = color;
        this.category = category;
        this.visibility = visibility;
        this.targetUserId = targetUserId;
        this.createdBy = createdBy;
    }

    public void update(String title, Instant startAt, Instant endAt, boolean allDay, String description,
                        String location, String color, CalendarEventCategory category,
                        CalendarEventVisibility visibility, Long targetUserId) {
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
        this.description = description;
        this.location = location;
        this.color = color;
        this.category = category;
        this.visibility = visibility;
        this.targetUserId = targetUserId;
    }
}
