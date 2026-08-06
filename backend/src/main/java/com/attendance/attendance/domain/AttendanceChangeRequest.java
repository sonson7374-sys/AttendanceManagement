package com.attendance.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "attendance_change_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "record_id")
    private Long recordId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 30)
    private ChangeRequestType changeType;

    @Column(name = "requested_check_in")
    private Instant requestedCheckIn;

    @Column(name = "requested_check_out")
    private Instant requestedCheckOut;

    @Column(name = "requested_workplace_id")
    private Long requestedWorkplaceId;

    @Column(name = "reason")
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

    public static AttendanceChangeRequest create(Long requesterId, Long recordId,
            LocalDate targetDate, ChangeRequestType changeType,
            Instant requestedCheckIn, Instant requestedCheckOut,
            Long requestedWorkplaceId, String reason) {
        AttendanceChangeRequest req = new AttendanceChangeRequest();
        req.requesterId = requesterId;
        req.recordId = recordId;
        req.targetDate = targetDate;
        req.changeType = changeType;
        req.requestedCheckIn = requestedCheckIn;
        req.requestedCheckOut = requestedCheckOut;
        req.requestedWorkplaceId = requestedWorkplaceId;
        req.reason = reason;
        req.status = ChangeRequestStatus.PENDING;
        return req;
    }

    public void approve(Long approverId) {
        this.status = ChangeRequestStatus.APPROVED;
        this.currentApproverId = approverId;
    }

    public void reject(Long approverId) {
        this.status = ChangeRequestStatus.REJECTED;
        this.currentApproverId = approverId;
    }

    public void cancel() {
        this.status = ChangeRequestStatus.CANCELED;
    }

    public boolean isPending() {
        return status == ChangeRequestStatus.PENDING;
    }
}
