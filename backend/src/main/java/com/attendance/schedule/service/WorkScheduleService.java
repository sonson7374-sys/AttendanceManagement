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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
     * resolveSchedule의 배치 버전. 출근부(지정일) 화면처럼 여러 직원의 근무제를 한 번에 표시해야 할 때,
     * 직원 수만큼 반복 조회하는 대신 배정·근무제·기본근무제를 각각 한 번씩만 조회해 메모리에서 매칭한다.
     * 개인 배정도 없고 소속 회사 기본 근무제도 없는 사용자는 결과 맵에서 제외한다(호출부에서 null로 처리).
     */
    @Transactional(readOnly = true)
    public Map<Long, WorkSchedule> resolveSchedules(List<Long> userIds, LocalDate date) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, UserWorkSchedule> latestAssignmentByUser = userWorkScheduleRepository
                .findEffectiveSchedules(userIds, date).stream()
                .collect(Collectors.toMap(UserWorkSchedule::getUserId, Function.identity(),
                        (a, b) -> a.getEffectiveFrom().isAfter(b.getEffectiveFrom()) ? a : b));

        Set<Long> workScheduleIds = latestAssignmentByUser.values().stream()
                .map(UserWorkSchedule::getWorkScheduleId).collect(Collectors.toSet());
        Map<Long, WorkSchedule> scheduleById = workScheduleRepository.findAllById(workScheduleIds).stream()
                .collect(Collectors.toMap(WorkSchedule::getId, Function.identity()));

        Map<Long, User> userById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, WorkSchedule> defaultScheduleByCompany = new HashMap<>();

        Map<Long, WorkSchedule> result = new HashMap<>();
        for (Long userId : userIds) {
            UserWorkSchedule assignment = latestAssignmentByUser.get(userId);
            WorkSchedule schedule = assignment != null ? scheduleById.get(assignment.getWorkScheduleId()) : null;
            if (schedule == null) {
                User user = userById.get(userId);
                if (user != null) {
                    schedule = defaultScheduleByCompany.computeIfAbsent(user.getCompanyId(),
                            companyId -> workScheduleRepository.findByCompanyIdAndDefaultScheduleTrue(companyId).orElse(null));
                }
            }
            if (schedule != null) {
                result.put(userId, schedule);
            }
        }
        return result;
    }

    /**
     * resolveSchedule의 "기간" 배치 버전. 근태조회(월별) 화면처럼 한 사용자가 여러 날짜(레코드)에 걸친
     * 근무제를 필요로 하고, 그 기간 중 근무제 배정이 바뀔 수도 있을 때 사용한다. 대상 기간과 유효기간이
     * 겹치는 배정을 한 번에 가져와 두고, 실제로 어떤 배정이 적용되는지는 반환된 {@link ScheduleResolver}가
     * 레코드별 날짜를 받아 메모리에서 골라준다 — 레코드 수만큼 반복 조회하지 않는다.
     */
    @Transactional(readOnly = true)
    public ScheduleResolver batchResolver(List<Long> userIds, LocalDate from, LocalDate to) {
        if (userIds.isEmpty()) {
            return new ScheduleResolver(Map.of(), Map.of(), Map.of(), Map.of());
        }

        Map<Long, List<UserWorkSchedule>> assignmentsByUser = userWorkScheduleRepository
                .findOverlapping(userIds, from, to).stream()
                .collect(Collectors.groupingBy(UserWorkSchedule::getUserId));

        Set<Long> workScheduleIds = assignmentsByUser.values().stream()
                .flatMap(List::stream).map(UserWorkSchedule::getWorkScheduleId).collect(Collectors.toSet());
        Map<Long, WorkSchedule> scheduleById = workScheduleRepository.findAllById(workScheduleIds).stream()
                .collect(Collectors.toMap(WorkSchedule::getId, Function.identity()));

        Map<Long, User> userById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, WorkSchedule> defaultScheduleByCompany = userById.values().stream()
                .map(User::getCompanyId).distinct()
                .collect(Collectors.toMap(Function.identity(),
                        companyId -> workScheduleRepository.findByCompanyIdAndDefaultScheduleTrue(companyId).orElse(null)));

        return new ScheduleResolver(assignmentsByUser, scheduleById, userById, defaultScheduleByCompany);
    }

    /** batchResolver가 미리 가져온 배정·근무제를 가지고, 레코드별 날짜에 맞는 근무제를 메모리에서 골라주는 조회기. */
    public static class ScheduleResolver {
        private final Map<Long, List<UserWorkSchedule>> assignmentsByUser;
        private final Map<Long, WorkSchedule> scheduleById;
        private final Map<Long, User> userById;
        private final Map<Long, WorkSchedule> defaultScheduleByCompany;

        private ScheduleResolver(Map<Long, List<UserWorkSchedule>> assignmentsByUser,
                                  Map<Long, WorkSchedule> scheduleById,
                                  Map<Long, User> userById,
                                  Map<Long, WorkSchedule> defaultScheduleByCompany) {
            this.assignmentsByUser = assignmentsByUser;
            this.scheduleById = scheduleById;
            this.userById = userById;
            this.defaultScheduleByCompany = defaultScheduleByCompany;
        }

        public WorkSchedule resolve(Long userId, LocalDate date) {
            UserWorkSchedule best = null;
            for (UserWorkSchedule a : assignmentsByUser.getOrDefault(userId, List.of())) {
                boolean effective = !a.getEffectiveFrom().isAfter(date)
                        && (a.getEffectiveUntil() == null || !a.getEffectiveUntil().isBefore(date));
                if (effective && (best == null || a.getEffectiveFrom().isAfter(best.getEffectiveFrom()))) {
                    best = a;
                }
            }
            WorkSchedule schedule = best != null ? scheduleById.get(best.getWorkScheduleId()) : null;
            if (schedule != null) {
                return schedule;
            }
            User user = userById.get(userId);
            return user != null ? defaultScheduleByCompany.get(user.getCompanyId()) : null;
        }
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
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.USER_NOT_FOUND));
        if (!targetUser.getCompanyId().equals(actor.getCompanyId())) {
            throw new AttendanceException(ErrorCode.USER_NOT_FOUND);
        }
        WorkSchedule workSchedule = workScheduleRepository.findById(workScheduleId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!workSchedule.getCompanyId().equals(actor.getCompanyId())) {
            throw new AttendanceException(ErrorCode.RESOURCE_NOT_FOUND);
        }

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
