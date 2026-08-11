package com.attendance.workplace.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_workplaces",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "workplace_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserWorkplace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "workplace_id", nullable = false)
    private Long workplaceId;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public UserWorkplace(Long userId, Long workplaceId, LocalDate validFrom,
                         LocalDate validTo, Long assignedBy) {
        this.userId = userId;
        this.workplaceId = workplaceId;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.assignedBy = assignedBy;
    }

    public boolean isActiveOn(LocalDate date) {
        if (validFrom != null && date.isBefore(validFrom)) return false;
        if (validTo != null && date.isAfter(validTo)) return false;
        return true;
    }

    public void closeOn(LocalDate validTo) {
        this.validTo = validTo;
    }

    /** 배정을 수행한 관리자 계정이 삭제될 때, 다른 사람의 배정 기록 자체는 남기고 배정자 참조만 끊는다. */
    public void detachAssigner() {
        this.assignedBy = null;
    }
}
