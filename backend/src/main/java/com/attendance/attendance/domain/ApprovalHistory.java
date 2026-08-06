package com.attendance.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "approval_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    /** 이 이력이 속한 신청 도메인. 기본값 CHANGE_REQUEST는 기존 근태 수정 요청과의 호환을 위함이다. */
    @Column(name = "request_type", nullable = false, length = 30)
    private String requestType;

    @Column(name = "approver_id", nullable = false)
    private Long approverId;

    @Column(nullable = false, length = 20)
    private String action;

    @Column
    private String comment;

    @CreationTimestamp
    @Column(name = "acted_at", nullable = false, updatable = false)
    private Instant actedAt;

    public static ApprovalHistory of(Long requestId, Long approverId, String action, String comment) {
        return of(requestId, "CHANGE_REQUEST", approverId, action, comment);
    }

    public static ApprovalHistory of(Long requestId, String requestType, Long approverId, String action, String comment) {
        ApprovalHistory h = new ApprovalHistory();
        h.requestId = requestId;
        h.requestType = requestType;
        h.approverId = approverId;
        h.action = action;
        h.comment = comment;
        return h;
    }
}
