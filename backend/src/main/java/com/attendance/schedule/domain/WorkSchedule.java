package com.attendance.schedule.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalTime;
import java.time.Instant;

@Entity
@Table(name = "work_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "work_start_time", nullable = false)
    private LocalTime workStartTime;

    @Column(name = "work_end_time", nullable = false)
    private LocalTime workEndTime;

    @Column(name = "required_work_minutes", nullable = false)
    private int requiredWorkMinutes;

    @Column(name = "overtime_threshold_min", nullable = false)
    private int overtimeThresholdMin;

    @Column(name = "schedule_type", nullable = false, length = 30)
    private String scheduleType;

    @Column(name = "late_threshold_minutes", nullable = false)
    private int lateThresholdMinutes;

    @Column(name = "early_leave_threshold_minutes", nullable = false)
    private int earlyLeaveThresholdMinutes;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    @Column(name = "night_shift_start")
    private LocalTime nightShiftStart;

    @Column(name = "night_shift_end")
    private LocalTime nightShiftEnd;

    @Column(name = "holiday_work_threshold_minutes", nullable = false)
    private int holidayWorkThresholdMinutes;

    @Column(name = "is_default", nullable = false)
    private boolean defaultSchedule;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public WorkSchedule(Long companyId, String name, LocalTime workStartTime, LocalTime workEndTime,
                        int requiredWorkMinutes, int overtimeThresholdMin, boolean defaultSchedule,
                        String scheduleType, int lateThresholdMinutes, int earlyLeaveThresholdMinutes,
                        int breakMinutes, LocalTime nightShiftStart, LocalTime nightShiftEnd,
                        int holidayWorkThresholdMinutes) {
        this.companyId = companyId;
        this.name = name;
        this.workStartTime = workStartTime;
        this.workEndTime = workEndTime;
        this.requiredWorkMinutes = requiredWorkMinutes;
        this.overtimeThresholdMin = overtimeThresholdMin;
        this.defaultSchedule = defaultSchedule;
        this.scheduleType = scheduleType != null ? scheduleType : "FIXED";
        this.lateThresholdMinutes = lateThresholdMinutes;
        this.earlyLeaveThresholdMinutes = earlyLeaveThresholdMinutes;
        this.breakMinutes = breakMinutes;
        this.nightShiftStart = nightShiftStart;
        this.nightShiftEnd = nightShiftEnd;
        this.holidayWorkThresholdMinutes = holidayWorkThresholdMinutes;
        this.active = true;
    }

    public void update(String name, LocalTime workStartTime, LocalTime workEndTime,
                       int requiredWorkMinutes, int overtimeThresholdMin,
                       String scheduleType, int lateThresholdMinutes, int earlyLeaveThresholdMinutes,
                       int breakMinutes, LocalTime nightShiftStart, LocalTime nightShiftEnd,
                       int holidayWorkThresholdMinutes) {
        this.name = name;
        this.workStartTime = workStartTime;
        this.workEndTime = workEndTime;
        this.requiredWorkMinutes = requiredWorkMinutes;
        this.overtimeThresholdMin = overtimeThresholdMin;
        this.scheduleType = scheduleType != null ? scheduleType : this.scheduleType;
        this.lateThresholdMinutes = lateThresholdMinutes;
        this.earlyLeaveThresholdMinutes = earlyLeaveThresholdMinutes;
        this.breakMinutes = breakMinutes;
        this.nightShiftStart = nightShiftStart;
        this.nightShiftEnd = nightShiftEnd;
        this.holidayWorkThresholdMinutes = holidayWorkThresholdMinutes;
    }

    public void deactivate() {
        this.active = false;
    }
}
