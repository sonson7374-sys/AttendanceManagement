package com.attendance.schedule.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_work_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserWorkSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "work_schedule_id", nullable = false)
    private Long workScheduleId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_until")
    private LocalDate effectiveUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public UserWorkSchedule(Long userId, Long workScheduleId, LocalDate effectiveFrom, LocalDate effectiveUntil) {
        this.userId = userId;
        this.workScheduleId = workScheduleId;
        this.effectiveFrom = effectiveFrom;
        this.effectiveUntil = effectiveUntil;
    }

    public void closeOn(LocalDate effectiveUntil) {
        this.effectiveUntil = effectiveUntil;
    }
}
