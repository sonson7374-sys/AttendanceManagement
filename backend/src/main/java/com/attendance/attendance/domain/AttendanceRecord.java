package com.attendance.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "attendance_records",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "work_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "check_in_at")
    private Instant checkInAt;

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    @Column(name = "workplace_id")
    private Long workplaceId;

    @Column(name = "check_in_latitude", precision = 10, scale = 7)
    private BigDecimal checkInLatitude;

    @Column(name = "check_in_longitude", precision = 10, scale = 7)
    private BigDecimal checkInLongitude;

    @Column(name = "check_in_distance_meters")
    private Integer checkInDistanceMeters;

    @Column(name = "check_in_accuracy_meters")
    private BigDecimal checkInAccuracyMeters;

    @Column(name = "check_out_latitude", precision = 10, scale = 7)
    private BigDecimal checkOutLatitude;

    @Column(name = "check_out_longitude", precision = 10, scale = 7)
    private BigDecimal checkOutLongitude;

    @Column(name = "check_out_distance_meters")
    private Integer checkOutDistanceMeters;

    @Column(name = "work_minutes")
    private Integer workMinutes;

    @Column(name = "break_minutes")
    private Integer breakMinutes;

    @Column(name = "overtime_minutes")
    private Integer overtimeMinutes;

    @Column(name = "is_late")
    private boolean late;

    @Column(name = "is_early_leave")
    private boolean earlyLeave;

    @Column(name = "is_closed")
    private boolean closed;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AttendanceRecord createAbsent(Long userId, LocalDate workDate) {
        AttendanceRecord record = new AttendanceRecord();
        record.userId = userId;
        record.workDate = workDate;
        record.status = AttendanceStatus.ABSENT;
        record.closed = true;
        return record;
    }

    public static AttendanceRecord createCheckIn(Long userId, LocalDate workDate, Long workplaceId,
                                                  Instant checkInAt, BigDecimal lat, BigDecimal lon,
                                                  Integer distanceMeters, BigDecimal accuracyMeters,
                                                  boolean late) {
        AttendanceRecord record = new AttendanceRecord();
        record.userId = userId;
        record.workDate = workDate;
        record.workplaceId = workplaceId;
        record.checkInAt = checkInAt;
        record.checkInLatitude = lat;
        record.checkInLongitude = lon;
        record.checkInDistanceMeters = distanceMeters;
        record.checkInAccuracyMeters = accuracyMeters;
        record.status = late ? AttendanceStatus.LATE : AttendanceStatus.WORKING;
        record.late = late;
        record.closed = false;
        return record;
    }

    public void checkOut(Instant checkOutAt, BigDecimal lat, BigDecimal lon,
                         Integer distanceMeters, int workMinutes, int breakMinutes,
                         int overtimeMinutes, boolean earlyLeave) {
        this.checkOutAt = checkOutAt;
        this.checkOutLatitude = lat;
        this.checkOutLongitude = lon;
        this.checkOutDistanceMeters = distanceMeters;
        this.workMinutes = workMinutes;
        this.breakMinutes = breakMinutes;
        this.overtimeMinutes = overtimeMinutes;
        this.earlyLeave = earlyLeave;
        this.status = earlyLeave ? AttendanceStatus.EARLY_LEAVE : AttendanceStatus.FINISHED;
    }

    public boolean hasCheckedIn() {
        return checkInAt != null;
    }

    public boolean hasCheckedOut() {
        return checkOutAt != null;
    }

    public boolean isWorking() {
        return status == AttendanceStatus.WORKING || status == AttendanceStatus.LATE;
    }

    public void startBreak() {
        this.status = AttendanceStatus.BREAK;
    }

    public void endBreak() {
        this.status = this.late ? AttendanceStatus.LATE : AttendanceStatus.WORKING;
    }

    /**
     * 근태 수정 요청 승인 시 출근 시각을 교정한다. 이미 퇴근·휴게 등으로 진행된 상태는
     * 건드리지 않고, 아직 근무 중(WORKING/LATE) 상태일 때만 지각 여부에 맞춰 상태를 갱신한다.
     */
    public void correctCheckIn(Instant newCheckInAt, boolean late) {
        this.checkInAt = newCheckInAt;
        this.late = late;
        if (this.status == AttendanceStatus.WORKING || this.status == AttendanceStatus.LATE) {
            this.status = late ? AttendanceStatus.LATE : AttendanceStatus.WORKING;
        }
    }

    /**
     * 근태 수정 요청 승인 시 퇴근 시각을 교정하고 근무·연장근무 시간, 조퇴 여부를 재계산한다.
     */
    public void correctCheckOut(Instant newCheckOutAt, int workMinutes, int breakMinutes,
                                 int overtimeMinutes, boolean earlyLeave) {
        this.checkOutAt = newCheckOutAt;
        this.workMinutes = workMinutes;
        this.breakMinutes = breakMinutes;
        this.overtimeMinutes = overtimeMinutes;
        this.earlyLeave = earlyLeave;
        if (this.status == AttendanceStatus.FINISHED || this.status == AttendanceStatus.EARLY_LEAVE) {
            this.status = earlyLeave ? AttendanceStatus.EARLY_LEAVE : AttendanceStatus.FINISHED;
        }
    }

    public void correctWorkplace(Long newWorkplaceId) {
        this.workplaceId = newWorkplaceId;
    }

    public void markAbsent() {
        this.status = AttendanceStatus.ABSENT;
        this.closed = true;
    }

    public static AttendanceRecord createManual(Long userId, LocalDate workDate, Long workplaceId,
                                                 Instant checkInAt, Instant checkOutAt,
                                                 AttendanceStatus status, Integer workMinutes,
                                                 Integer breakMinutes, Integer overtimeMinutes,
                                                 boolean late, boolean earlyLeave) {
        AttendanceRecord record = new AttendanceRecord();
        record.userId = userId;
        record.workDate = workDate;
        record.workplaceId = workplaceId;
        record.checkInAt = checkInAt;
        record.checkOutAt = checkOutAt;
        record.status = status;
        record.workMinutes = workMinutes;
        record.breakMinutes = breakMinutes;
        record.overtimeMinutes = overtimeMinutes;
        record.late = late;
        record.earlyLeave = earlyLeave;
        record.closed = false;
        return record;
    }

    public void applyAdminCorrection(Instant checkInAt, Instant checkOutAt, Long workplaceId,
                                      AttendanceStatus status, Integer workMinutes,
                                      Integer breakMinutes, Integer overtimeMinutes,
                                      Boolean late, Boolean earlyLeave) {
        if (checkInAt != null) this.checkInAt = checkInAt;
        if (checkOutAt != null) this.checkOutAt = checkOutAt;
        if (workplaceId != null) this.workplaceId = workplaceId;
        if (status != null) this.status = status;
        if (workMinutes != null) this.workMinutes = workMinutes;
        if (breakMinutes != null) this.breakMinutes = breakMinutes;
        if (overtimeMinutes != null) this.overtimeMinutes = overtimeMinutes;
        // checkInAt/checkOutAt이 보정되면 지각·조퇴 여부도 새 시각 기준으로 다시 반영해야
        // 홈 화면 등에서 낡은 지각·조퇴 배지가 남지 않는다.
        if (late != null) this.late = late;
        if (earlyLeave != null) this.earlyLeave = earlyLeave;
    }

    public void close() {
        this.closed = true;
    }

    public void reopen() {
        this.closed = false;
    }
}
