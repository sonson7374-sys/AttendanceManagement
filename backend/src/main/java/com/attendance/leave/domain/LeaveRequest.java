package com.attendance.leave.domain;

import com.attendance.attendance.domain.ChangeRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "leave_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "employee_number", nullable = false, length = 6)
    private String employeeNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 30)
    private LeaveRequestType requestType;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column
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

    public static LeaveRequest create(Long requesterId, String employeeNumber, LeaveRequestType requestType,
                                       Instant startAt, Instant endAt, String reason) {
        LeaveRequest req = new LeaveRequest();
        req.requesterId = requesterId;
        req.employeeNumber = employeeNumber;
        req.requestType = requestType;
        req.startAt = startAt;
        req.endAt = endAt;
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

    public boolean isPending() {
        return status == ChangeRequestStatus.PENDING;
    }
}
