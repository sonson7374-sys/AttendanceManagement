package com.attendance.calendar.service;

import com.attendance.audit.service.AuditLogService;
import com.attendance.calendar.domain.CalendarEvent;
import com.attendance.calendar.domain.CalendarEventVisibility;
import com.attendance.calendar.dto.CalendarEventCreateRequest;
import com.attendance.calendar.dto.CalendarEventResponse;
import com.attendance.calendar.dto.CalendarEventUpdateRequest;
import com.attendance.calendar.repository.CalendarEventRepository;
import com.attendance.common.config.AppConfig;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.user.domain.User;
import com.attendance.user.domain.UserPrincipal;
import com.attendance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * 일정관리(캘린더). "전체" 일정은 로그인한 모든 사용자에게 보이고, "개인" 일정은 등록한 본인에게만 보인다
 * (개인 일정은 대상 직원을 별도로 지정하지 않고 항상 작성자 본인 기준으로 등록된다).
 * - "전체" 공개범위로 등록/수정하는 것은 권한레벨(level, 그룹코드 LEVEL_ROLL)이 SYSADMIN/HRADMIN/PRESIDENT인
 *   계정만 가능하다(승인함의 승인/반려 권한과 동일한 기준).
 * - "개인" 일정은 인증된 모든 사용자가 등록할 수 있고, 본인의 개인 일정만 수정·삭제할 수 있다.
 * - 위 권한레벨 계정은 전체/개인 구분 없이 모든 일정을 등록·수정·삭제할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private static final Set<String> SCHEDULE_ADMIN_LEVELS = Set.of("SYSADMIN", "HRADMIN", "PRESIDENT");

    private final CalendarEventRepository calendarEventRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public java.util.List<CalendarEventResponse> list(Long actorId, LocalDate from, LocalDate to) {
        User actor = findUser(actorId);
        Instant rangeStart = from.atStartOfDay(AppConfig.SEOUL).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(AppConfig.SEOUL).toInstant();

        java.util.List<CalendarEvent> events = SCHEDULE_ADMIN_LEVELS.contains(actor.getLevel())
                ? calendarEventRepository.findAllInRange(rangeStart, rangeEnd)
                : calendarEventRepository.findVisibleInRange(rangeStart, rangeEnd, actorId, CalendarEventVisibility.ALL);

        return events.stream().map(this::toResponse).toList();
    }

    @Transactional
    public CalendarEventResponse create(UserPrincipal actor, CalendarEventCreateRequest req) {
        if (req.getVisibility() == CalendarEventVisibility.ALL) {
            requireScheduleAdmin(actor);
        }
        validateRange(req.getStartAt(), req.getEndAt());

        CalendarEvent event = CalendarEvent.builder()
                .title(req.getTitle())
                .startAt(req.getStartAt().toInstant())
                .endAt(req.getEndAt().toInstant())
                .allDay(req.isAllDay())
                .description(req.getDescription())
                .location(req.getLocation())
                .color(req.getColor())
                .category(req.getCategory())
                .visibility(req.getVisibility())
                .targetUserId(req.getVisibility() == CalendarEventVisibility.PERSONAL ? actor.getId() : null)
                .createdBy(actor.getId())
                .build();
        event = calendarEventRepository.save(event);

        auditLogService.record(actor.getId(), actor.getUsername(), "CALENDAR_EVENT_CREATED", "CALENDAR_EVENT",
                event.getId(), Map.of("title", event.getTitle()));

        return toResponse(event);
    }

    @Transactional
    public CalendarEventResponse update(UserPrincipal actor, Long id, CalendarEventUpdateRequest req) {
        CalendarEvent event = calendarEventRepository.findById(id)
                .orElseThrow(() -> new AttendanceException(ErrorCode.RESOURCE_NOT_FOUND));
        requireCanManage(actor, event);
        if (req.getVisibility() == CalendarEventVisibility.ALL) {
            requireScheduleAdmin(actor);
        }
        validateRange(req.getStartAt(), req.getEndAt());

        // 개인 일정은 대상을 별도로 지정받지 않는다 — 이미 개인 일정이었다면 기존 대상(작성자)을 유지하고,
        // 전체→개인으로 바뀌는 경우에는 수정하는 본인을 대상으로 삼는다.
        Long targetUserId = req.getVisibility() == CalendarEventVisibility.PERSONAL
                ? (event.getVisibility() == CalendarEventVisibility.PERSONAL && event.getTargetUserId() != null
                        ? event.getTargetUserId() : actor.getId())
                : null;

        event.update(
                req.getTitle(),
                req.getStartAt().toInstant(),
                req.getEndAt().toInstant(),
                req.isAllDay(),
                req.getDescription(),
                req.getLocation(),
                req.getColor(),
                req.getCategory(),
                req.getVisibility(),
                targetUserId);

        auditLogService.record(actor.getId(), actor.getUsername(), "CALENDAR_EVENT_UPDATED", "CALENDAR_EVENT",
                event.getId(), Map.of("title", event.getTitle()));

        return toResponse(event);
    }

    @Transactional
    public void delete(UserPrincipal actor, Long id) {
        CalendarEvent event = calendarEventRepository.findById(id)
                .orElseThrow(() -> new AttendanceException(ErrorCode.RESOURCE_NOT_FOUND));
        requireCanManage(actor, event);
        calendarEventRepository.delete(event);
        auditLogService.record(actor.getId(), actor.getUsername(), "CALENDAR_EVENT_DELETED", "CALENDAR_EVENT",
                id, Map.of("title", event.getTitle()));
    }

    private void validateRange(java.time.OffsetDateTime startAt, java.time.OffsetDateTime endAt) {
        if (endAt.isBefore(startAt)) {
            throw new AttendanceException(ErrorCode.INVALID_INPUT, "종료 일시는 시작 일시보다 빠를 수 없습니다.");
        }
    }

    private void requireScheduleAdmin(UserPrincipal actor) {
        if (!SCHEDULE_ADMIN_LEVELS.contains(actor.getLevel())) {
            throw new AttendanceException(ErrorCode.ACCESS_DENIED);
        }
    }

    /** 권한레벨 계정은 모든 일정을 관리할 수 있고, 그 외에는 본인의 개인 일정만 관리할 수 있다. */
    private void requireCanManage(UserPrincipal actor, CalendarEvent event) {
        if (SCHEDULE_ADMIN_LEVELS.contains(actor.getLevel())) {
            return;
        }
        boolean ownsPersonalEvent = event.getVisibility() == CalendarEventVisibility.PERSONAL
                && actor.getId().equals(event.getTargetUserId());
        if (!ownsPersonalEvent) {
            throw new AttendanceException(ErrorCode.ACCESS_DENIED);
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + userId));
    }

    private CalendarEventResponse toResponse(CalendarEvent e) {
        String targetUserName = e.getTargetUserId() != null
                ? userRepository.findById(e.getTargetUserId()).map(User::getName).orElse(null)
                : null;
        String createdByName = userRepository.findById(e.getCreatedBy()).map(User::getName).orElse(null);
        return CalendarEventResponse.from(e, targetUserName, createdByName);
    }
}
