package com.attendance.schedule.domain;

import com.attendance.attendance.domain.ChangeRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "work_schedule_change_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkScheduleChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    /** 신청 시점에 신청자가 배정되어 있던 근무제(있는 경우). 승인 시 신규 근무제로 교체된다. */
    @Column(name = "current_work_schedule_id")
    private Long currentWorkScheduleId;

    /** 신청자가 기존 근무제 목록 중에서 선택한, 변경을 희망하는 근무제. */
    @Column(name = "target_work_schedule_id", nullable = false)
    private Long targetWorkScheduleId;

    /** 적용 예정월의 1일. */
    @Column(name = "effective_month", nullable = false)
    private LocalDate effectiveMonth;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeRequestStatus status;

    @Column(name = "current_approver_id")
    private Long currentApproverId;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public WorkScheduleChangeRequest(Long requesterId, Long currentWorkScheduleId, Long targetWorkScheduleId,
                                      LocalDate effectiveMonth, String reason) {
        this.requesterId = requesterId;
        this.currentWorkScheduleId = currentWorkScheduleId;
        this.targetWorkScheduleId = targetWorkScheduleId;
        this.effectiveMonth = effectiveMonth;
        this.reason = reason;
        this.status = ChangeRequestStatus.PENDING;
    }

    public boolean isPending() {
        return status == ChangeRequestStatus.PENDING;
    }

    public void approve(Long approverId) {
        this.status = ChangeRequestStatus.APPROVED;
        this.currentApproverId = approverId;
    }

    public void reject(Long approverId) {
        this.status = ChangeRequestStatus.REJECTED;
        this.currentApproverId = approverId;
    }

    /** 승인자 계정이 삭제될 때, 다른 사람이 제출한 이 신청 자체는 남기고 승인자 참조만 끊는다. */
    public void detachApprover() {
        this.currentApproverId = null;
    }
}
