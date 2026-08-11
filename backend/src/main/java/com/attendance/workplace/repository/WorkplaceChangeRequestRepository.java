package com.attendance.workplace.repository;

import com.attendance.attendance.domain.ChangeRequestStatus;
import com.attendance.workplace.domain.WorkplaceChangeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WorkplaceChangeRequestRepository extends JpaRepository<WorkplaceChangeRequest, Long> {

    List<WorkplaceChangeRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    List<WorkplaceChangeRequest> findByCurrentApproverId(Long currentApproverId);

    List<WorkplaceChangeRequest> findByStatusOrderByCreatedAtAsc(ChangeRequestStatus status);

    Page<WorkplaceChangeRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<WorkplaceChangeRequest> findByStatusOrderByCreatedAtDesc(ChangeRequestStatus status, Pageable pageable);

    List<WorkplaceChangeRequest> findByStatusAndRequesterIdInOrderByCreatedAtAsc(
            ChangeRequestStatus status, Collection<Long> requesterIds);

    Page<WorkplaceChangeRequest> findByRequesterIdInOrderByCreatedAtDesc(
            Collection<Long> requesterIds, Pageable pageable);

    Page<WorkplaceChangeRequest> findByStatusAndRequesterIdInOrderByCreatedAtDesc(
            ChangeRequestStatus status, Collection<Long> requesterIds, Pageable pageable);
}
