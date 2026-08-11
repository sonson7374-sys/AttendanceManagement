package com.attendance.attendance.repository;

import com.attendance.attendance.domain.ApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {

    List<ApprovalHistory> findByRequestIdOrderByActedAtDesc(Long requestId);

    // request_id는 requestType(엔티티 5종)에 걸쳐 재사용되는 값이라 type 없이 조회하면 다른 신청의
    // 이력과 섞일 수 있다. 사용자 삭제 시 "본인이 제출한 신청"의 이력만 정확히 골라 지우기 위해 사용.
    List<ApprovalHistory> findByRequestIdInAndRequestType(List<Long> requestIds, String requestType);

    // 이 사용자가 다른 사람의 신청을 승인/반려한 이력(다른 신청 소유자의 데이터이므로 삭제하지 않고
    // 승인자 연결만 끊는다).
    List<ApprovalHistory> findByApproverId(Long approverId);
}
