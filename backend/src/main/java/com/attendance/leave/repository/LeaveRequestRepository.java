package com.attendance.leave.repository;

import com.attendance.attendance.domain.ChangeRequestStatus;
import com.attendance.leave.domain.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    List<LeaveRequest> findByStatusOrderByCreatedAtAsc(ChangeRequestStatus status);

    Page<LeaveRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<LeaveRequest> findByStatusOrderByCreatedAtDesc(ChangeRequestStatus status, Pageable pageable);

    List<LeaveRequest> findByStatusAndRequesterIdInOrderByCreatedAtAsc(
            ChangeRequestStatus status, Collection<Long> requesterIds);

    Page<LeaveRequest> findByRequesterIdInOrderByCreatedAtDesc(
            Collection<Long> requesterIds, Pageable pageable);

    Page<LeaveRequest> findByStatusAndRequesterIdInOrderByCreatedAtDesc(
            ChangeRequestStatus status, Collection<Long> requesterIds, Pageable pageable);

    /** rangeStart~rangeEnd 기간과 겹치는(startAt~endAt) 요청을 조회한다 (캘린더 표시용). */
    List<LeaveRequest> findByStatusAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtAsc(
            ChangeRequestStatus status, Instant rangeEnd, Instant rangeStart);

    /**
     * requesterIds 중 rangeStart~rangeEnd 기간과 겹치는 승인된 휴가를 조회한다 (출근부 지정일 화면 등).
     * 일괄등록(엑셀)으로 승인된 휴가는 근태 기록에 자동 반영되지 않으므로, 화면에서 휴가 표시가
     * 필요할 때는 attendance_records만 보지 말고 이 조회로 leave_requests도 함께 확인해야 한다.
     */
    List<LeaveRequest> findByStatusAndRequesterIdInAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
            ChangeRequestStatus status, Collection<Long> requesterIds, Instant rangeEnd, Instant rangeStart);
}
