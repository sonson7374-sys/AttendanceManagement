package com.attendance.attendance.service;

import com.attendance.attendance.domain.*;
import com.attendance.attendance.dto.*;
import com.attendance.attendance.repository.*;
import com.attendance.common.config.AppConfig;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.config.AttendanceProperties;
import com.attendance.config.GeofenceProperties;
import com.attendance.geofence.service.GeofenceService;
import com.attendance.geofence.service.GeofenceService.WorkplaceDistance;
import com.attendance.schedule.domain.WorkSchedule;
import com.attendance.schedule.service.WorkScheduleService;
import com.attendance.workplace.domain.Workplace;
import com.attendance.workplace.repository.WorkplaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";

    private final AttendanceRecordRepository recordRepository;
    private final AttendanceEventRepository eventRepository;
    private final BreakRecordRepository breakRecordRepository;
    private final WorkplaceRepository workplaceRepository;
    private final GeofenceService geofenceService;
    private final GeofenceProperties geofenceProperties;
    private final AttendanceProperties attendanceProperties;
    private final WorkScheduleService workScheduleService;
    private final AttendanceScheduleEvaluator scheduleEvaluator;
    private final RedisTemplate<String, String> redisTemplate;
    private final Clock clock;

    /**
     * 클라이언트가 Idempotency-Key를 보낸 경우, 동일 키로 이미 처리 중이거나 처리된 요청이면 거부한다.
     * 네트워크 재시도로 인한 출근/퇴근 중복 제출을 방지하기 위함이다.
     */
    private void checkIdempotency(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                IDEMPOTENCY_KEY_PREFIX + idempotencyKey, "1",
                Duration.ofSeconds(attendanceProperties.getDuplicateRequestWindowSeconds()));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new AttendanceException(ErrorCode.DUPLICATE_REQUEST);
        }
    }

    /**
     * 출근 처리.
     * - GPS 검증 → 근무지 지오펜스 판정 → 중복 확인 → 원본 이벤트 + 일별 요약 동시 저장
     */
    @Transactional
    public AttendanceResponse checkIn(Long userId, CheckInRequest req, String idempotencyKey) {
        checkIdempotency(idempotencyKey);

        // 1. GPS 검증
        geofenceService.validateGpsInput(
                req.getLatitude().doubleValue(),
                req.getLongitude().doubleValue(),
                req.getAccuracyMeters().doubleValue(),
                req.getCapturedAt().toInstant(),
                req.isMockLocationDetected());

        // 1-1. 직전 GPS 이벤트 대비 이동속도 검증 (서버 자체 모의위치 탐지)
        eventRepository.findFirstByUserIdOrderByEventAtDesc(userId).ifPresent(last ->
                geofenceService.validateMovementPlausibility(
                        last.getLatitude(), last.getLongitude(), last.getEventAt(),
                        req.getLatitude().doubleValue(), req.getLongitude().doubleValue(),
                        req.getCapturedAt().toInstant()));

        // 2. 서버 시각 기준 근무일 결정 (Asia/Seoul)
        LocalDate workDate = Instant.now(clock).atZone(AppConfig.SEOUL).toLocalDate();

        // 3. 허용 근무지 조회 및 지오펜스 판정
        List<Workplace> assigned = workplaceRepository.findAssignedWorkplacesByUserIdAndDate(userId, workDate);
        WorkplaceDistance closest = geofenceService.findClosest(
                assigned, req.getLatitude().doubleValue(), req.getLongitude().doubleValue());
        if (!closest.isWithinGeofence() && !geofenceProperties.isAllowOutsideCheckIn()) {
            throw new AttendanceException(ErrorCode.OUTSIDE_GEOFENCE,
                    String.format("가장 가까운 근무지까지 %.0fm (허용 반경: %dm)",
                            closest.getDistanceMeters(), closest.getWorkplace().getRadiusMeters()));
        }
        if (!closest.getWorkplace().isCheckInAllowed()) {
            throw new AttendanceException(ErrorCode.WORKPLACE_CHECK_IN_NOT_ALLOWED);
        }

        // 4. 중복 출근 확인
        if (recordRepository.existsByUserIdAndWorkDate(userId, workDate)) {
            throw new AttendanceException(ErrorCode.ALREADY_CHECKED_IN);
        }

        // 5. 근무제 조회 및 지각 여부 판정 (공휴일 출근은 지각 판정에서 제외)
        WorkSchedule schedule = workScheduleService.resolveSchedule(userId, workDate);
        boolean late = scheduleEvaluator.isLate(req.getCapturedAt().toInstant(), workDate, schedule);

        // 6. 출근 기록 + 원본 이벤트 저장 (같은 트랜잭션)
        AttendanceRecord record;
        try {
            record = recordRepository.save(AttendanceRecord.createCheckIn(
                    userId, workDate, closest.getWorkplace().getId(),
                    req.getCapturedAt().toInstant(),
                    req.getLatitude(), req.getLongitude(),
                    (int) Math.round(closest.getDistanceMeters()),
                    req.getAccuracyMeters(), late));
        } catch (DataIntegrityViolationException e) {
            // DB unique constraint (user_id, work_date)가 동시 요청을 최종 차단
            throw new AttendanceException(ErrorCode.ALREADY_CHECKED_IN);
        }

        eventRepository.save(AttendanceEvent.builder()
                .userId(userId)
                .recordId(record.getId())
                .eventType(EventType.CHECK_IN)
                .eventAt(req.getCapturedAt().toInstant())
                .workplaceId(closest.getWorkplace().getId())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .accuracyMeters(req.getAccuracyMeters())
                .distanceMeters((int) Math.round(closest.getDistanceMeters()))
                .deviceId(req.getDeviceId())
                .devicePlatform(req.getDevicePlatform())
                .mockDetected(req.isMockLocationDetected())
                .build());

        log.info("CheckIn userId={} workDate={} workplaceId={} distanceM={} late={}",
                userId, workDate, closest.getWorkplace().getId(),
                (int) Math.round(closest.getDistanceMeters()), late);

        return AttendanceResponse.fromCheckIn(record, closest.getWorkplace().getName(),
                closest.getDistanceMeters());
    }

    /**
     * 퇴근 처리.
     * - 출근 기록 확인 → GPS 검증 → 근무시간 계산(근무제 휴게시간 차감) → 이벤트 저장
     */
    @Transactional
    public AttendanceResponse checkOut(Long userId, CheckOutRequest req, String idempotencyKey) {
        checkIdempotency(idempotencyKey);

        // 1. 오늘 출근 기록 조회
        LocalDate workDate = Instant.now(clock).atZone(AppConfig.SEOUL).toLocalDate();
        AttendanceRecord record = recordRepository.findByUserIdAndWorkDate(userId, workDate)
                .orElseThrow(() -> new AttendanceException(ErrorCode.NOT_CHECKED_IN));

        if (record.hasCheckedOut()) {
            throw new AttendanceException(ErrorCode.ALREADY_CHECKED_OUT);
        }
        if (record.isClosed()) {
            throw new AttendanceException(ErrorCode.ATTENDANCE_CLOSED);
        }
        if (breakRecordRepository.findByRecordIdAndEndAtIsNull(record.getId()).isPresent()) {
            throw new AttendanceException(ErrorCode.BREAK_NOT_ENDED);
        }

        // 2. GPS 검증
        geofenceService.validateGpsInput(
                req.getLatitude().doubleValue(),
                req.getLongitude().doubleValue(),
                req.getAccuracyMeters().doubleValue(),
                req.getCapturedAt().toInstant(),
                req.isMockLocationDetected());

        // 2-1. 직전 GPS 이벤트 대비 이동속도 검증 (서버 자체 모의위치 탐지)
        eventRepository.findFirstByUserIdOrderByEventAtDesc(userId).ifPresent(last ->
                geofenceService.validateMovementPlausibility(
                        last.getLatitude(), last.getLongitude(), last.getEventAt(),
                        req.getLatitude().doubleValue(), req.getLongitude().doubleValue(),
                        req.getCapturedAt().toInstant()));

        // 3. 퇴근 근무지 조회 (출근 근무지 기준 재검증)
        List<Workplace> assigned = workplaceRepository.findAssignedWorkplacesByUserIdAndDate(userId, workDate);
        WorkplaceDistance closest = geofenceService.findClosest(
                assigned, req.getLatitude().doubleValue(), req.getLongitude().doubleValue());
        if (!closest.isWithinGeofence() && !geofenceProperties.isAllowOutsideCheckOut()) {
            throw new AttendanceException(ErrorCode.OUTSIDE_GEOFENCE,
                    String.format("가장 가까운 근무지까지 %.0fm (허용 반경: %dm)",
                            closest.getDistanceMeters(), closest.getWorkplace().getRadiusMeters()));
        }
        if (!closest.getWorkplace().isCheckOutAllowed()) {
            throw new AttendanceException(ErrorCode.WORKPLACE_CHECK_OUT_NOT_ALLOWED);
        }

        // 4. 근무제 조회
        WorkSchedule schedule = workScheduleService.resolveSchedule(userId, workDate);

        // 5. 근무 시간 계산 (휴게시간은 근무제에 설정된 고정값을 사용)
        Instant checkOutTime = req.getCapturedAt().toInstant();
        long totalMinutes = Duration.between(record.getCheckInAt(), checkOutTime).toMinutes();
        long breakMinutes = schedule.getBreakMinutes();
        long workMinutes = Math.max(0, totalMinutes - breakMinutes);
        // 잔업시간은 "근무스케줄 외 근무시간"(조기 출근 + 늦은 퇴근)과 같은 기준으로 계산해 저장한다.
        long overtimeMinutes = scheduleEvaluator.computeOutsideScheduleMinutes(
                record.getCheckInAt(), checkOutTime, workDate, schedule);

        // 6. 조퇴 판정
        boolean earlyLeave = scheduleEvaluator.isEarlyLeave(checkOutTime, schedule);

        // 7. 퇴근 기록 업데이트 + 원본 이벤트 저장
        record.checkOut(checkOutTime, req.getLatitude(), req.getLongitude(),
                (int) Math.round(closest.getDistanceMeters()),
                (int) workMinutes, (int) breakMinutes, (int) overtimeMinutes, earlyLeave);

        eventRepository.save(AttendanceEvent.builder()
                .userId(userId)
                .recordId(record.getId())
                .eventType(EventType.CHECK_OUT)
                .eventAt(checkOutTime)
                .workplaceId(closest.getWorkplace().getId())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .accuracyMeters(req.getAccuracyMeters())
                .distanceMeters((int) Math.round(closest.getDistanceMeters()))
                .deviceId(req.getDeviceId())
                .devicePlatform(req.getDevicePlatform())
                .mockDetected(req.isMockLocationDetected())
                .build());

        log.info("CheckOut userId={} workDate={} workMinutes={} earlyLeave={}",
                userId, workDate, workMinutes, earlyLeave);

        return AttendanceResponse.fromCheckOut(record, closest.getWorkplace().getName(),
                closest.getDistanceMeters());
    }

    /**
     * 휴게 시작. 근무 중(WORKING/LATE) 상태에서만 가능하며, 이미 진행 중인 휴게가 있으면 거부한다.
     */
    @Transactional
    public AttendanceResponse startBreak(Long userId) {
        LocalDate workDate = Instant.now(clock).atZone(AppConfig.SEOUL).toLocalDate();
        AttendanceRecord record = recordRepository.findByUserIdAndWorkDate(userId, workDate)
                .orElseThrow(() -> new AttendanceException(ErrorCode.NOT_CHECKED_IN));

        if (!record.isWorking()) {
            throw new AttendanceException(ErrorCode.CANNOT_START_BREAK);
        }
        if (breakRecordRepository.findByRecordIdAndEndAtIsNull(record.getId()).isPresent()) {
            throw new AttendanceException(ErrorCode.BREAK_ALREADY_STARTED);
        }

        Instant now = Instant.now(clock);
        record.startBreak();
        breakRecordRepository.save(BreakRecord.builder()
                .recordId(record.getId())
                .startAt(now)
                .build());

        eventRepository.save(AttendanceEvent.builder()
                .userId(userId)
                .recordId(record.getId())
                .eventType(EventType.BREAK_START)
                .eventAt(now)
                .mockDetected(false)
                .build());

        log.info("BreakStart userId={} recordId={}", userId, record.getId());

        return AttendanceResponse.fromBreak(record, resolveWorkplaceName(record.getWorkplaceId()));
    }

    /**
     * 휴게 종료. 진행 중인 휴게가 없으면 거부하고, 있으면 종료 후 지각 여부에 맞춰 근무 상태로 되돌린다.
     */
    @Transactional
    public AttendanceResponse endBreak(Long userId) {
        LocalDate workDate = Instant.now(clock).atZone(AppConfig.SEOUL).toLocalDate();
        AttendanceRecord record = recordRepository.findByUserIdAndWorkDate(userId, workDate)
                .orElseThrow(() -> new AttendanceException(ErrorCode.NOT_CHECKED_IN));

        BreakRecord ongoing = breakRecordRepository.findByRecordIdAndEndAtIsNull(record.getId())
                .orElseThrow(() -> new AttendanceException(ErrorCode.NOT_ON_BREAK));

        Instant now = Instant.now(clock);
        ongoing.end(now);
        record.endBreak();

        eventRepository.save(AttendanceEvent.builder()
                .userId(userId)
                .recordId(record.getId())
                .eventType(EventType.BREAK_END)
                .eventAt(now)
                .mockDetected(false)
                .build());

        log.info("BreakEnd userId={} recordId={} durationMinutes={}",
                userId, record.getId(), ongoing.durationMinutes());

        return AttendanceResponse.fromBreak(record, resolveWorkplaceName(record.getWorkplaceId()));
    }

    private String resolveWorkplaceName(Long workplaceId) {
        if (workplaceId == null) return null;
        return workplaceRepository.findById(workplaceId).map(Workplace::getName).orElse(null);
    }

    /**
     * 오늘 근태 현황 조회.
     */
    @Transactional(readOnly = true)
    public TodayAttendanceResponse getTodayAttendance(Long userId) {
        LocalDate today = Instant.now(clock).atZone(AppConfig.SEOUL).toLocalDate();
        return recordRepository.findByUserIdAndWorkDate(userId, today)
                .map(record -> {
                    String workplaceName = record.getWorkplaceId() == null ? null :
                            workplaceRepository.findById(record.getWorkplaceId())
                                    .map(Workplace::getName).orElse(null);
                    WorkSchedule schedule = workScheduleService.resolveSchedule(userId, today);
                    boolean late = scheduleEvaluator.isLate(record.getCheckInAt(), today, schedule);
                    boolean earlyLeave = scheduleEvaluator.isEarlyLeave(record.getCheckOutAt(), schedule);
                    return TodayAttendanceResponse.from(record, workplaceName, late, earlyLeave);
                })
                .orElse(TodayAttendanceResponse.absent(today));
    }
}
