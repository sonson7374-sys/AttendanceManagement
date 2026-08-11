package com.attendance.outsidework.repository;

import com.attendance.attendance.domain.ChangeRequestStatus;
import com.attendance.outsidework.domain.OutsideWorkRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OutsideWorkRequestRepository extends JpaRepository<OutsideWorkRequest, Long> {

    List<OutsideWorkRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    List<OutsideWorkRequest> findByCurrentApproverId(Long currentApproverId);

    List<OutsideWorkRequest> findByStatusOrderByCreatedAtAsc(ChangeRequestStatus status);

    Page<OutsideWorkRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<OutsideWorkRequest> findByStatusOrderByCreatedAtDesc(ChangeRequestStatus status, Pageable pageable);

    List<OutsideWorkRequest> findByStatusAndRequesterIdInOrderByCreatedAtAsc(
            ChangeRequestStatus status, Collection<Long> requesterIds);

    Page<OutsideWorkRequest> findByRequesterIdInOrderByCreatedAtDesc(
            Collection<Long> requesterIds, Pageable pageable);

    Page<OutsideWorkRequest> findByStatusAndRequesterIdInOrderByCreatedAtDesc(
            ChangeRequestStatus status, Collection<Long> requesterIds, Pageable pageable);
}
