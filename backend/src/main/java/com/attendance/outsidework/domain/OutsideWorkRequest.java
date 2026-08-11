package com.attendance.outsidework.domain;

import com.attendance.attendance.domain.ChangeRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "outside_work_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutsideWorkRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 30)
    private OutsideWorkRequestType requestType;

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

    @Column(name = "destination_address", length = 500)
    private String destinationAddress;

    @Column(name = "destination_latitude", precision = 10, scale = 7)
    private BigDecimal destinationLatitude;

    @Column(name = "destination_longitude", precision = 10, scale = 7)
    private BigDecimal destinationLongitude;

    @Column(name = "temp_radius_meters")
    private Integer tempRadiusMeters;

    @Column(name = "visit_purpose", length = 500)
    private String visitPurpose;

    @Column(name = "client_name", length = 200)
    private String clientName;

    @Column(name = "expected_return_at")
    private Instant expectedReturnAt;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public OutsideWorkRequest(Long requesterId, OutsideWorkRequestType requestType,
                               Instant startAt, Instant endAt, String reason,
                               String destinationAddress, BigDecimal destinationLatitude,
                               BigDecimal destinationLongitude, Integer tempRadiusMeters,
                               String visitPurpose, String clientName, Instant expectedReturnAt) {
        this.requesterId = requesterId;
        this.requestType = requestType;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reason = reason;
        this.destinationAddress = destinationAddress;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.tempRadiusMeters = tempRadiusMeters;
        this.visitPurpose = visitPurpose;
        this.clientName = clientName;
        this.expectedReturnAt = expectedReturnAt;
        this.status = ChangeRequestStatus.PENDING;
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

    /** 승인자 계정이 삭제될 때, 다른 사람이 제출한 이 신청 자체는 남기고 승인자 참조만 끊는다. */
    public void detachApprover() {
        this.currentApproverId = null;
    }
}
