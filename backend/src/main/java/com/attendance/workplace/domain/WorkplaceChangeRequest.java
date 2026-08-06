package com.attendance.workplace.domain;

import com.attendance.attendance.domain.ChangeRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "workplace_change_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkplaceChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    /** 신청 당시 신청자가 배정되어 있던 근무지(있는 경우). 승인 시 이 배정을 해제하고 신규 근무지로 교체한다. */
    @Column(name = "current_workplace_id")
    private Long currentWorkplaceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String address;

    @Column(name = "detail_address", length = 200)
    private String detailAddress;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "radius_meters", nullable = false)
    private Integer radiusMeters;

    @Column(name = "max_accuracy_meters")
    private Integer maxAccuracyMeters;

    @Column(name = "check_in_allowed", nullable = false)
    private boolean checkInAllowed;

    @Column(name = "check_out_allowed", nullable = false)
    private boolean checkOutAllowed;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeRequestStatus status;

    /** 승인 시 실제로 생성된 근무지의 id. */
    @Column(name = "resulting_workplace_id")
    private Long resultingWorkplaceId;

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
    public WorkplaceChangeRequest(Long requesterId, Long currentWorkplaceId, String name, String address,
                                   String detailAddress, String type, BigDecimal latitude, BigDecimal longitude,
                                   Integer radiusMeters, Integer maxAccuracyMeters,
                                   boolean checkInAllowed, boolean checkOutAllowed,
                                   LocalDate effectiveDate, String reason) {
        this.requesterId = requesterId;
        this.currentWorkplaceId = currentWorkplaceId;
        this.name = name;
        this.address = address;
        this.detailAddress = detailAddress;
        this.type = type != null ? type : "OFFICE";
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters != null ? radiusMeters : 100;
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.checkInAllowed = checkInAllowed;
        this.checkOutAllowed = checkOutAllowed;
        this.effectiveDate = effectiveDate;
        this.reason = reason;
        this.status = ChangeRequestStatus.PENDING;
    }

    public boolean isPending() {
        return status == ChangeRequestStatus.PENDING;
    }

    public void approve(Long approverId, Long resultingWorkplaceId) {
        this.status = ChangeRequestStatus.APPROVED;
        this.currentApproverId = approverId;
        this.resultingWorkplaceId = resultingWorkplaceId;
    }

    public void reject(Long approverId) {
        this.status = ChangeRequestStatus.REJECTED;
        this.currentApproverId = approverId;
    }
}
