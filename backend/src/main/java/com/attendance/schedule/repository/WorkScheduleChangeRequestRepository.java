package com.attendance.schedule.repository;

import com.attendance.attendance.domain.ChangeRequestStatus;
import com.attendance.schedule.domain.WorkScheduleChangeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WorkScheduleChangeRequestRepository extends JpaRepository<WorkScheduleChangeRequest, Long> {

    List<WorkScheduleChangeRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    List<WorkScheduleChangeRequest> findByStatusOrderByCreatedAtAsc(ChangeRequestStatus status);

    Page<WorkScheduleChangeRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<WorkScheduleChangeRequest> findByStatusOrderByCreatedAtDesc(ChangeRequestStatus status, Pageable pageable);

    List<WorkScheduleChangeRequest> findByStatusAndRequesterIdInOrderByCreatedAtAsc(
            ChangeRequestStatus status, Collection<Long> requesterIds);

    Page<WorkScheduleChangeRequest> findByRequesterIdInOrderByCreatedAtDesc(
            Collection<Long> requesterIds, Pageable pageable);

    Page<WorkScheduleChangeRequest> findByStatusAndRequesterIdInOrderByCreatedAtDesc(
            ChangeRequestStatus status, Collection<Long> requesterIds, Pageable pageable);
}
