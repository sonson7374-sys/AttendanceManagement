package com.attendance.attendance.repository;

import com.attendance.attendance.domain.AttendanceChangeRequest;
import com.attendance.attendance.domain.ChangeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ChangeRequestRepository extends JpaRepository<AttendanceChangeRequest, Long> {

    List<AttendanceChangeRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    List<AttendanceChangeRequest> findByCurrentApproverId(Long currentApproverId);

    List<AttendanceChangeRequest> findByStatusOrderByCreatedAtAsc(ChangeRequestStatus status);

    Page<AttendanceChangeRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AttendanceChangeRequest> findByStatusOrderByCreatedAtDesc(ChangeRequestStatus status, Pageable pageable);

    List<AttendanceChangeRequest> findByStatusAndRequesterIdInOrderByCreatedAtAsc(
            ChangeRequestStatus status, Collection<Long> requesterIds);

    Page<AttendanceChangeRequest> findByRequesterIdInOrderByCreatedAtDesc(
            Collection<Long> requesterIds, Pageable pageable);

    Page<AttendanceChangeRequest> findByStatusAndRequesterIdInOrderByCreatedAtDesc(
            ChangeRequestStatus status, Collection<Long> requesterIds, Pageable pageable);

    List<AttendanceChangeRequest> findByStatusAndRequesterIdInAndTargetDate(
            ChangeRequestStatus status, Collection<Long> requesterIds, LocalDate targetDate);
}
