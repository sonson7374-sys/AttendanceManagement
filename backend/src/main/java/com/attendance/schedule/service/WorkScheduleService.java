package com.attendance.schedule.service;

import com.attendance.audit.service.AuditLogService;
import com.attendance.schedule.domain.UserWorkSchedule;
import com.attendance.schedule.domain.WorkSchedule;
import com.attendance.schedule.repository.UserWorkScheduleRepository;
import com.attendance.schedule.repository.WorkScheduleRepository;
import com.attendance.user.domain.User;
import com.attendance.user.domain.UserPrincipal;
import com.attendance.user.repository.UserRepository;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkScheduleService {

    private final WorkScheduleRepository workScheduleRepository;
    private final UserWorkScheduleRepository userWorkScheduleRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /**
     * userId 와 날짜 기준으로 적용할 근무제를 반환한다.
     * 1) 개인 배정 근무제 우선
     * 2) 없으면 회사 기본 근무제
     * 3) 그것도 없으면 예외
     */
    @Transactional(readOnly = true)
    public WorkSchedule resolveSchedule(Long userId, LocalDate date) {
        return userWorkScheduleRepository.findEffectiveSchedule(userId, date)
                .flatMap(uws -> workScheduleRepository.findById(uws.getWorkScheduleId()))
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new AttendanceException(ErrorCode.USER_NOT_FOUND));
                    return workScheduleRepository
                            .findByCompanyIdAndDefaultScheduleTrue(user.getCompanyId())
                            .orElseThrow(() -> new AttendanceException(ErrorCode.INTERNAL_ERROR));
                });
    }

    /**
     * 특정 직원에게 근무제를 배정한다. 기존에 종료일 없이 열려있던 배정은 오늘 이전일자로 종료 처리하고,
     * 오늘부터 시작하는 새 배정을 추가한다(같은 날 재배정 시에는 기존 배정을 대체한다).
     */
    @Transactional
    public void assignWorkSchedule(Long userId, Long workScheduleId, UserPrincipal actor) {
        assignWorkSchedule(userId, workScheduleId, LocalDate.now(clock), actor);
    }

    /**
     * effectiveFrom을 직접 지정해 근무제를 배정한다(근무제 변경요청 승인 등 미래 적용일 지정이 필요한 경우 사용).
     * 기존에 종료일 없이 열려있던 배정은 effectiveFrom 이전일자로 종료 처리하거나(이미 시작된 배정),
     * effectiveFrom 이후 시작이라면 대체(삭제)한다.
     */
    @Transactional
    public void assignWorkSchedule(Long userId, Long workScheduleId, LocalDate effectiveFrom, UserPrincipal actor) {
        if (!userRepository.existsById(userId)) {
            throw new AttendanceException(ErrorCode.USER_NOT_FOUND);
        }
        WorkSchedule workSchedule = workScheduleRepository.findById(workScheduleId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.RESOURCE_NOT_FOUND));

        var current = userWorkScheduleRepository
                .findFirstByUserIdAndEffectiveUntilIsNullOrderByEffectiveFromDesc(userId);
        if (current.isPresent() && current.get().getWorkScheduleId().equals(workScheduleId)) {
            return;
        }
        current.ifPresent(existing -> {
            if (existing.getEffectiveFrom().isBefore(effectiveFrom)) {
                existing.closeOn(effectiveFrom.minusDays(1));
            } else {
                userWorkScheduleRepository.delete(existing);
            }
        });

        userWorkScheduleRepository.save(UserWorkSchedule.builder()
                .userId(userId)
                .workScheduleId(workScheduleId)
                .effectiveFrom(effectiveFrom)
                .effectiveUntil(null)
                .build());

        auditLogService.record(actor.getId(), actor.getUsername(), "USER_WORK_SCHEDULE_ASSIGNED",
                "USER", userId, Map.of("workScheduleId", workScheduleId, "workScheduleName", workSchedule.getName()));
    }
}
