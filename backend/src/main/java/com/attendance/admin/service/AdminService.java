package com.attendance.admin.service;

import com.attendance.admin.dto.AdminAttendanceBoardRow;
import com.attendance.admin.dto.AttendanceScopeInfoResponse;
import com.attendance.admin.dto.AdminAttendanceCorrectionRequest;
import com.attendance.admin.dto.AdminAttendanceResponse;
import com.attendance.admin.dto.AdminManualAttendanceRequest;
import com.attendance.admin.dto.AdminMonthlyUserSummary;
import com.attendance.admin.dto.DashboardStatsResponse;
import com.attendance.admin.dto.DepartmentAttendanceRate;
import com.attendance.admin.dto.HourlyAttendancePoint;
import com.attendance.commoncode.domain.CommonCode;
import com.attendance.commoncode.repository.CommonCodeRepository;
import com.attendance.organization.domain.Organization;
import com.attendance.organization.repository.OrganizationRepository;
import com.attendance.organization.service.OrganizationScopeService;
import com.attendance.attendance.domain.AttendanceRecord;
import com.attendance.attendance.domain.AttendanceStatus;
import com.attendance.attendance.domain.ChangeRequestStatus;
import com.attendance.attendance.repository.AttendanceRecordRepository;
import com.attendance.attendance.repository.ChangeRequestRepository;
import com.attendance.audit.service.AuditLogService;
import com.attendance.common.config.AppConfig;
import com.attendance.attendance.service.AttendanceScheduleEvaluator;
import com.attendance.holiday.repository.HolidayRepository;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.leave.domain.LeaveRequestType;
import com.attendance.leave.repository.LeaveRequestRepository;
import com.attendance.notification.domain.NotificationType;
import com.attendance.notification.service.NotificationService;
import com.attendance.schedule.domain.WorkSchedule;
import com.attendance.schedule.service.WorkScheduleService;
import com.attendance.user.domain.User;
import com.attendance.user.domain.UserRole;
import com.attendance.user.domain.UserStatus;
import com.attendance.user.repository.UserRepository;
import com.attendance.workplace.domain.UserWorkplace;
import com.attendance.workplace.domain.Workplace;
import com.attendance.workplace.repository.UserWorkplaceRepository;
import com.attendance.workplace.repository.WorkplaceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class AdminService {

    /**
     * 출근부(지정일) 화면의 근태상황 열에 표시할 휴가 유형별 정확한 명칭.
     * 연장근무/휴일근무/조기퇴근 신청은 하루 전체를 쉬는 휴가가 아니라 근무 관련 승인 전용 신청이라 제외한다.
     */
    private static final Map<LeaveRequestType, String> LEAVE_BOARD_TYPE_LABELS = Map.of(
            LeaveRequestType.ANNUAL, "연차",
            LeaveRequestType.HALF_DAY, "반차",
            LeaveRequestType.HOURLY, "반반차",
            LeaveRequestType.SICK, "병가",
            LeaveRequestType.OFFICIAL, "공가",
            LeaveRequestType.ZERO_DAY, "대체휴가");

    private final UserRepository userRepository;
    private final AttendanceRecordRepository recordRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final WorkplaceRepository workplaceRepository;
    private final UserWorkplaceRepository userWorkplaceRepository;
    private final OrganizationRepository organizationRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final WorkScheduleService workScheduleService;
    private final AttendanceScheduleEvaluator scheduleEvaluator;
    private final HolidayRepository holidayRepository;
    private final OrganizationScopeService organizationScopeService;
    private final CommonCodeRepository commonCodeRepository;
    private final Clock clock;

    private static final String LEVEL_GROUP_CODE = "LEVEL_ROLL";

    // 출근부(일괄수정) 등에서 관리자가 근무(분)을 직접 입력하지 않고 출근·퇴근 시각만 입력한 경우,
    // 점심 휴게시간(12:00~13:00, 1시간)을 제외한 근무시간을 서버가 계산해 저장한다.
    private static final int DEFAULT_BREAK_MINUTES = 60;

    /**
     * 명시적으로 입력된 근무(분)이 있으면 그대로 쓰고, 없고 출근·퇴근 시각이 모두 있으면
     * (퇴근-출근 경과분 - 휴게(분))으로 근무(분)을 계산한다. 계산 불가하면(퇴근시각 없음 등) null을 반환한다.
     */
    private Integer[] resolveWorkAndBreakMinutes(Instant checkInAt, Instant checkOutAt,
                                                  Integer explicitWorkMinutes, Integer explicitBreakMinutes) {
        if (explicitWorkMinutes != null) {
            return new Integer[]{explicitWorkMinutes, explicitBreakMinutes != null ? explicitBreakMinutes : DEFAULT_BREAK_MINUTES};
        }
        if (checkInAt == null || checkOutAt == null) {
            return new Integer[]{null, explicitBreakMinutes};
        }
        int breakMinutes = explicitBreakMinutes != null ? explicitBreakMinutes : DEFAULT_BREAK_MINUTES;
        long elapsedMinutes = Duration.between(checkInAt, checkOutAt).toMinutes();
        int workMinutes = (int) Math.max(0, elapsedMinutes - breakMinutes);
        return new Integer[]{workMinutes, breakMinutes};
    }

    /**
     * 명시적으로 입력된 잔업(분)이 있으면 그대로 쓰고, 없으면 "근무스케줄 외 근무시간"과 동일한 기준
     * (조기 출근 + 늦은 퇴근)으로 계산한다. 출퇴근 시각이나 근무제를 특정할 수 없으면 null을 반환한다
     * (기존 저장값을 건드리지 않음 — AttendanceRecord.applyAdminCorrection/createManual 참고).
     */
    private Integer resolveOvertimeMinutes(Instant checkInAt, Instant checkOutAt, LocalDate workDate,
                                            WorkSchedule schedule, Integer explicitOvertimeMinutes) {
        if (explicitOvertimeMinutes != null) {
            return explicitOvertimeMinutes;
        }
        if (checkInAt == null || checkOutAt == null || schedule == null) {
            return null;
        }
        return scheduleEvaluator.computeOutsideScheduleMinutes(checkInAt, checkOutAt, workDate, schedule);
    }

    private boolean computeLate(AttendanceRecord record) {
        if (record.getCheckInAt() == null) {
            return false;
        }
        try {
            WorkSchedule schedule = workScheduleService.resolveSchedule(record.getUserId(), record.getWorkDate());
            return scheduleEvaluator.isLate(record.getCheckInAt(), record.getWorkDate(), schedule);
        } catch (AttendanceException e) {
            return false;
        }
    }

    private boolean computeEarlyLeave(AttendanceRecord record) {
        if (record.getCheckOutAt() == null) {
            return false;
        }
        try {
            WorkSchedule schedule = workScheduleService.resolveSchedule(record.getUserId(), record.getWorkDate());
            return scheduleEvaluator.isEarlyLeave(record.getCheckOutAt(), schedule);
        } catch (AttendanceException e) {
            return false;
        }
    }

    // 근무제와 공휴일 목록을 이미 배치 조회로 가져와 둔 화면(출근부 지정일, 월별 조회 등)에서,
    // 레코드마다 다시 조회하지 않고 미리 구해 둔 값을 그대로 넘겨 받아 계산하는 버전.
    private boolean computeLate(AttendanceRecord record, WorkSchedule schedule, Set<LocalDate> holidayDates) {
        if (record.getCheckInAt() == null || schedule == null) {
            return false;
        }
        return scheduleEvaluator.isLate(record.getCheckInAt(), record.getWorkDate(), schedule, holidayDates);
    }

    private boolean computeEarlyLeave(AttendanceRecord record, WorkSchedule schedule) {
        if (record.getCheckOutAt() == null || schedule == null) {
            return false;
        }
        return scheduleEvaluator.isEarlyLeave(record.getCheckOutAt(), schedule);
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats(Long companyId) {
        LocalDate today = Instant.now(clock).atZone(AppConfig.SEOUL).toLocalDate();

        Set<Long> companyUserIds = userRepository.findByCompanyId(companyId).stream()
                .map(User::getId).collect(Collectors.toSet());
        long totalEmployees = userRepository.findByCompanyId(companyId).stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE).count();

        List<AttendanceRecord> todayRecords = recordRepository.findByWorkDate(today).stream()
                .filter(r -> companyUserIds.contains(r.getUserId()))
                .toList();
        long presentToday = todayRecords.stream()
                .filter(r -> r.getStatus() == AttendanceStatus.WORKING
                          || r.getStatus() == AttendanceStatus.BREAK
                          || r.getStatus() == AttendanceStatus.FINISHED
                          || r.getStatus() == AttendanceStatus.EARLY_LEAVE)
                .count();

        // 지각 판정을 오늘 출근한 레코드마다 개별 조회하면 출근자 수만큼 DB 왕복이 반복되므로,
        // 오늘 레코드에 등장하는 사용자들의 근무제와 공휴일 여부를 각각 한 번씩만 조회해 메모리에서 매칭한다.
        List<Long> todayUserIds = todayRecords.stream().map(AttendanceRecord::getUserId).distinct().toList();
        Map<Long, WorkSchedule> todayScheduleByUser = workScheduleService.resolveSchedules(todayUserIds, today);
        Set<LocalDate> todayHolidayDates = new HashSet<>(holidayRepository.findHolidayDatesBetween(today, today));
        long lateToday = todayRecords.stream()
                .filter(r -> computeLate(r, todayScheduleByUser.get(r.getUserId()), todayHolidayDates))
                .count();
        long absentToday = todayRecords.stream()
                .filter(r -> r.getStatus() == AttendanceStatus.ABSENT)
                .count();
        long onLeaveToday = todayRecords.stream()
                .filter(r -> r.getStatus() == AttendanceStatus.LEAVE)
                .count();
        long outsideWorkToday = todayRecords.stream()
                .filter(r -> r.getStatus() == AttendanceStatus.OUTSIDE_WORK
                          || r.getStatus() == AttendanceStatus.BUSINESS_TRIP
                          || r.getStatus() == AttendanceStatus.REMOTE_WORK)
                .count();
        long checkedOutToday = todayRecords.stream()
                .filter(r -> r.getStatus() == AttendanceStatus.FINISHED)
                .count();
        long pendingApprovals = changeRequestRepository
                .findByStatusAndRequesterIdInOrderByCreatedAtAsc(ChangeRequestStatus.PENDING, companyUserIds).size();

        return DashboardStatsResponse.builder()
                .totalEmployees(totalEmployees)
                .presentToday(presentToday)
                .lateToday(lateToday)
                .absentToday(absentToday)
                .onLeaveToday(onLeaveToday)
                .outsideWorkToday(outsideWorkToday)
                .checkedOutToday(checkedOutToday)
                .pendingApprovals(pendingApprovals)
                .departmentAttendanceRates(getDepartmentAttendanceRates(companyId, todayRecords))
                .hourlyAttendance(getHourlyAttendance(todayRecords))
                .build();
    }

    private List<DepartmentAttendanceRate> getDepartmentAttendanceRates(Long companyId, List<AttendanceRecord> todayRecords) {
        List<Organization> organizations = organizationRepository.findByCompanyIdAndActive(companyId, true);
        // 상위부서명 표시를 위해, 비활성 처리된 상위부서도 이름을 찾을 수 있도록 회사 전체 조직을 기준으로 맵을 만든다.
        Map<Long, Organization> orgById = organizationRepository.findByCompanyId(companyId).stream()
                .collect(Collectors.toMap(Organization::getId, o -> o, (a, b) -> a));
        List<User> activeUsers = userRepository.findByCompanyId(companyId).stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .toList();
        java.util.Set<Long> presentUserIds = todayRecords.stream()
                .filter(r -> r.getStatus() != AttendanceStatus.ABSENT)
                .map(AttendanceRecord::getUserId)
                .collect(Collectors.toSet());

        return organizations.stream()
                .map(org -> {
                    List<User> orgUsers = activeUsers.stream()
                            .filter(u -> org.getId().equals(u.getOrganizationId()))
                            .toList();
                    long total = orgUsers.size();
                    long present = orgUsers.stream().filter(u -> presentUserIds.contains(u.getId())).count();
                    double rate = total == 0 ? 0.0 : Math.round((present * 1000.0) / total) / 10.0;
                    Organization parent = org.getParentId() != null ? orgById.get(org.getParentId()) : null;
                    return DepartmentAttendanceRate.builder()
                            .organizationId(org.getId())
                            .organizationName(org.getName())
                            .parentOrganizationName(parent != null ? parent.getName() : null)
                            .presentCount(present)
                            .totalCount(total)
                            .rate(rate)
                            .build();
                })
                // 인원이 1명뿐인 부서는 출근율이 0%/100%로만 나와 의미가 없으므로 2명 이상인 부서만 보여준다.
                .filter(d -> d.getTotalCount() > 1)
                .toList();
    }

    /** 오늘 출퇴근 기록을 시간(0~23시)별로 묶어 시간대별 출퇴근 인원 추이를 만든다. 06~21시 구간만 보여준다. */
    private List<HourlyAttendancePoint> getHourlyAttendance(List<AttendanceRecord> todayRecords) {
        Map<Integer, Long> checkInCountByHour = todayRecords.stream()
                .filter(r -> r.getCheckInAt() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getCheckInAt().atZone(AppConfig.SEOUL).getHour(),
                        Collectors.counting()));
        Map<Integer, Long> checkOutCountByHour = todayRecords.stream()
                .filter(r -> r.getCheckOutAt() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getCheckOutAt().atZone(AppConfig.SEOUL).getHour(),
                        Collectors.counting()));

        return IntStream.rangeClosed(6, 21)
                .mapToObj(hour -> HourlyAttendancePoint.builder()
                        .hour(String.format("%02d시", hour))
                        .checkInCount(checkInCountByHour.getOrDefault(hour, 0L))
                        .checkOutCount(checkOutCountByHour.getOrDefault(hour, 0L))
                        .build())
                .toList();
    }

    /**
     * 조직 목록을 "상위부서 다음에 그 소속 하위부서들"이 바로 이어지는 트리 순서로 펼쳐, 각 조직 id가
     * 그 순서에서 몇 번째인지 매핑한다(같은 레벨끼리는 display_order, 없으면 이름 순). 상위부서가 목록에
     * 없는 조직(비활성 처리 등)은 최상위로 취급해 누락되지 않게 한다.
     */
    private Map<Long, Integer> buildOrgSortIndex(List<Organization> organizations) {
        Set<Long> ids = organizations.stream().map(Organization::getId).collect(Collectors.toSet());
        Map<Long, List<Organization>> byParent = new HashMap<>();
        for (Organization org : organizations) {
            Long key = org.getParentId() != null && ids.contains(org.getParentId()) ? org.getParentId() : null;
            byParent.computeIfAbsent(key, k -> new ArrayList<>()).add(org);
        }
        for (List<Organization> siblings : byParent.values()) {
            siblings.sort(Comparator
                    .comparing((Organization o) -> o.getDisplayOrder() != null ? o.getDisplayOrder() : 0)
                    .thenComparing(Organization::getName));
        }

        Map<Long, Integer> index = new HashMap<>();
        int[] counter = {0};
        visitOrgsForSortIndex(null, byParent, index, counter);
        return index;
    }

    private void visitOrgsForSortIndex(Long parentId, Map<Long, List<Organization>> byParent,
                                        Map<Long, Integer> index, int[] counter) {
        for (Organization org : byParent.getOrDefault(parentId, List.of())) {
            index.put(org.getId(), counter[0]++);
            visitOrgsForSortIndex(org.getId(), byParent, index, counter);
        }
    }

    /**
     * 역할(role)이 아니라 권한레벨(LEVEL_ROLL) 기준으로 조회 범위를 정한다
     * (직원 레벨은 본인만, 파트장 이상 레벨은 본인 조직 산하 + 본인, SYSADMIN/HRADMIN/PRESIDENT는 전체).
     */
    @Transactional(readOnly = true)
    public AdminAttendanceResponse getById(Long recordId, Long actorId) {
        AttendanceRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.ATTENDANCE_RECORD_NOT_FOUND));
        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        if (visibleUserIds != null && !visibleUserIds.contains(record.getUserId())) {
            throw new AttendanceException(ErrorCode.ACCESS_DENIED);
        }
        return toAdminAttendanceResponse(record);
    }

    /**
     * 근태조회(일별) 화면. 근태조회(월별)와 동일하게 역할(role)이 아니라 권한레벨(LEVEL_ROLL) 기준으로
     * 조회 범위를 정한다(직원 레벨은 본인만, 파트장 이상 레벨은 본인 조직 산하 + 본인).
     */
    @Transactional(readOnly = true)
    public Page<AdminAttendanceResponse> getDailyAttendance(
            LocalDate date, Long workplaceId, Long organizationId, String employeeName,
            AttendanceStatus status, Boolean lateOnly, Boolean locationValid, Pageable pageable, Long actorId) {
        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        List<AttendanceRecord> records = recordRepository.findByWorkDate(date);

        // 사용자·근무지·근무제·공휴일을 레코드마다 반복 조회하면 그날 레코드 수만큼 DB 왕복이 반복되므로,
        // 필요한 대상을 각각 한 번에 모아 조회해 메모리에서 매칭한다.
        List<Long> recordUserIds = records.stream().map(AttendanceRecord::getUserId).distinct().toList();
        Map<Long, User> userById = userRepository.findAllById(recordUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<Long> recordWorkplaceIds = records.stream().map(AttendanceRecord::getWorkplaceId)
                .filter(id -> id != null).distinct().toList();
        Map<Long, Workplace> workplaceById = workplaceRepository.findAllById(recordWorkplaceIds).stream()
                .collect(Collectors.toMap(Workplace::getId, w -> w));
        Map<Long, WorkSchedule> scheduleByUser = workScheduleService.resolveSchedules(recordUserIds, date);
        Set<LocalDate> holidayDates = new HashSet<>(holidayRepository.findHolidayDatesBetween(date, date));

        List<AdminAttendanceResponse> responses = records.stream()
                .filter(r -> visibleUserIds == null || visibleUserIds.contains(r.getUserId()))
                .filter(r -> workplaceId == null || workplaceId.equals(r.getWorkplaceId()))
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> lateOnly == null || !lateOnly
                        || computeLate(r, scheduleByUser.get(r.getUserId()), holidayDates))
                .map(record -> {
                    User user = userById.get(record.getUserId());
                    Workplace workplace = record.getWorkplaceId() == null ? null : workplaceById.get(record.getWorkplaceId());
                    return new Object[]{record, user, workplace};
                })
                .filter(arr -> {
                    User user = (User) arr[1];
                    if (organizationId != null && (user == null || !organizationId.equals(user.getOrganizationId()))) {
                        return false;
                    }
                    if (employeeName != null && !employeeName.isBlank()) {
                        if (user == null || !user.getName().toLowerCase().contains(employeeName.toLowerCase())) {
                            return false;
                        }
                    }
                    return true;
                })
                .filter(arr -> {
                    if (locationValid == null) return true;
                    AttendanceRecord record = (AttendanceRecord) arr[0];
                    Workplace workplace = (Workplace) arr[2];
                    boolean withinRange = workplace != null && record.getCheckInDistanceMeters() != null
                            && record.getCheckInDistanceMeters() <= workplace.getRadiusMeters();
                    return locationValid == withinRange;
                })
                .map(arr -> {
                    AttendanceRecord record = (AttendanceRecord) arr[0];
                    User user = (User) arr[1];
                    Workplace workplace = (Workplace) arr[2];
                    String userName = user != null ? user.getName() : "(삭제됨)";
                    String empNumber = user != null ? user.getEmployeeNumber() : "";
                    String workplaceName = workplace != null ? workplace.getName() : null;
                    WorkSchedule schedule = scheduleByUser.get(record.getUserId());
                    return AdminAttendanceResponse.from(record, userName, empNumber, workplaceName,
                            computeLate(record, schedule, holidayDates), computeEarlyLeave(record, schedule));
                })
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), responses.size());
        if (start > end) start = end;
        return new PageImpl<>(responses.subList(start, end), pageable, responses.size());
    }

    /**
     * 근태조회(월별) 화면. 역할(role)이 아니라 권한레벨(LEVEL_ROLL) 기준으로 조회 범위를 정한다
     * (직원 레벨은 본인만, 파트장 이상 레벨은 본인 조직 산하 + 본인, SYSADMIN/HRADMIN/PRESIDENT는 전체).
     */
    @Transactional(readOnly = true)
    public List<AdminMonthlyUserSummary> getMonthlySummary(int year, int month, Long actorId) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        List<AttendanceRecord> records = recordRepository.findByWorkDateBetweenAllUsers(from, to);
        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE).stream()
                .filter(u -> visibleUserIds == null || visibleUserIds.contains(u.getId()))
                .toList();

        Map<Long, List<AttendanceRecord>> byUser = records.stream()
                .collect(Collectors.groupingBy(AttendanceRecord::getUserId));

        // 레코드(그 달의 출근일)마다 근무제·공휴일 여부를 다시 조회하면 "직원 수 × 근무일수"만큼 DB 왕복이
        // 반복되므로, 이 기간에 해당하는 근무제 배정과 공휴일을 각각 한 번에 가져와 메모리에서 매칭한다.
        List<Long> activeUserIds = activeUsers.stream().map(User::getId).toList();
        WorkScheduleService.ScheduleResolver scheduleResolver = workScheduleService.batchResolver(activeUserIds, from, to);
        Set<LocalDate> holidayDates = new HashSet<>(holidayRepository.findHolidayDatesBetween(from, to));

        return activeUsers.stream().map(user -> {
            List<AttendanceRecord> userRecords = byUser.getOrDefault(user.getId(), List.of());
            int presentDays = (int) userRecords.stream()
                    .filter(r -> r.getStatus() != AttendanceStatus.ABSENT).count();
            int lateDays = (int) userRecords.stream()
                    .filter(r -> computeLate(r, scheduleResolver.resolve(r.getUserId(), r.getWorkDate()), holidayDates))
                    .count();
            int earlyLeaveDays = (int) userRecords.stream()
                    .filter(r -> computeEarlyLeave(r, scheduleResolver.resolve(r.getUserId(), r.getWorkDate())))
                    .count();
            int absentDays = (int) userRecords.stream()
                    .filter(r -> r.getStatus() == AttendanceStatus.ABSENT).count();
            int totalWork = userRecords.stream()
                    .mapToInt(r -> r.getWorkMinutes() != null ? r.getWorkMinutes() : 0).sum();
            int totalOvertime = userRecords.stream()
                    .mapToInt(r -> r.getOvertimeMinutes() != null ? r.getOvertimeMinutes() : 0).sum();

            return AdminMonthlyUserSummary.builder()
                    .userId(user.getId())
                    .userName(user.getName())
                    .employeeNumber(user.getEmployeeNumber())
                    .workingDays(userRecords.size())
                    .presentDays(presentDays)
                    .lateDays(lateDays)
                    .earlyLeaveDays(earlyLeaveDays)
                    .absentDays(absentDays)
                    .totalWorkMinutes(totalWork)
                    .totalOvertimeMinutes(totalOvertime)
                    .build();
        }).toList();
    }

    /**
     * 근태조회(일별) 화면이 로그인 계정의 레벨에 따라 UI를 어떻게 보여줄지 판단하기 위한 정보.
     * 직원 레벨이면 본인 근태만 월별로 간단히 보여주고, 파트장 이상이어도 하위 직원이 없으면
     * 근무지/부서/직원명 검색 필터는 숨긴다(대상이 본인뿐이라 필터가 의미가 없음).
     * organizationIds/workplaceIds는 그 필터들의 후보를 본인이 조회 가능한 범위로 좁히기 위한 값이다
     * (SYSADMIN/HRADMIN/PRESIDENT는 null=전체, 그 외는 본인 조직 산하 조직들 / 그 범위 직원들이 실제 배정된 근무지들).
     */
    @Transactional(readOnly = true)
    public AttendanceScopeInfoResponse getAttendanceScopeInfo(Long actorId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.USER_NOT_FOUND));
        boolean partLeadOrAbove = organizationScopeService.isPartLeadOrAbove(actor.getLevel());
        Set<Long> managedUserIds = organizationScopeService.resolveManagedUserIds(actorId);
        boolean hasSubordinates = partLeadOrAbove && (managedUserIds == null || managedUserIds.size() > 1);

        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        Set<Long> visibleOrgIds = organizationScopeService.resolveVisibleOrganizationIdsByLevel(actorId);
        List<Long> organizationIds = visibleOrgIds == null ? null : new ArrayList<>(visibleOrgIds);
        List<Long> workplaceIds = visibleUserIds == null ? null : userWorkplaceRepository.findByUserIdIn(visibleUserIds)
                .stream().map(UserWorkplace::getWorkplaceId).distinct().toList();

        return AttendanceScopeInfoResponse.builder()
                .employeeLevel(!partLeadOrAbove)
                .hasSubordinates(hasSubordinates)
                .organizationIds(organizationIds)
                .workplaceIds(workplaceIds)
                .build();
    }

    /**
     * 출근부(지정일) 화면. 파트장 이상 권한(레벨)만 조회할 수 있고, 조직 계층에 따라 범위가 다르다
     * (HR_ADMIN/SYSTEM_ADMIN은 전체, 그 외 파트장 이상 레벨은 본인 조직 산하 + 본인).
     */
    @Transactional(readOnly = true)
    public List<AdminAttendanceBoardRow> getRegisterBoard(LocalDate date, Long organizationId,
                                                           String employeeName, Long actorId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.USER_NOT_FOUND));
        boolean topAdmin = actor.getRole() == UserRole.HR_ADMIN || actor.getRole() == UserRole.SYSTEM_ADMIN;
        if (!topAdmin && !organizationScopeService.isPartLeadOrAbove(actor.getLevel())) {
            throw new AttendanceException(ErrorCode.ACCESS_DENIED);
        }
        // 조직 목록은 관리 범위 계산(조직 트리 순회)과 정렬(부서 트리 순번)에 모두 필요하므로 한 번만 조회해 재사용한다
        // — 그렇지 않으면 조직 트리 순회가 하위 부서 수만큼 쿼리를 반복하게 된다.
        List<Organization> allOrganizations = organizationRepository.findAll();
        Set<Long> managedUserIds = organizationScopeService.resolveManagedUserIds(actorId, allOrganizations);

        // 상위부서 → 소속부서 순으로 조직 트리를 펼친 순번(같은 부서 안에서는 팀장 이상이 먼저, 그 다음 이름 순)으로 정렬한다.
        Map<Long, Integer> orgSortIndex = buildOrgSortIndex(allOrganizations);
        Map<String, Integer> levelOrder = commonCodeRepository.findByGroupCodeOrderByDisplayOrderAsc(LEVEL_GROUP_CODE)
                .stream().collect(Collectors.toMap(CommonCode::getCode, CommonCode::getDisplayOrder));

        List<User> targets = userRepository.findByStatus(UserStatus.ACTIVE).stream()
                .filter(u -> managedUserIds == null || managedUserIds.contains(u.getId()))
                .filter(u -> organizationId == null || organizationId.equals(u.getOrganizationId()))
                .filter(u -> employeeName == null || employeeName.isBlank()
                        || u.getName().toLowerCase().contains(employeeName.toLowerCase()))
                .sorted(Comparator
                        .comparing((User u) -> orgSortIndex.getOrDefault(u.getOrganizationId(), Integer.MAX_VALUE))
                        .thenComparing(u -> levelOrder.getOrDefault(u.getLevel(), Integer.MAX_VALUE))
                        .thenComparing(User::getName))
                .toList();

        List<Long> targetIds = targets.stream().map(User::getId).toList();
        Map<Long, AttendanceRecord> recordByUser = recordRepository.findByWorkDate(date).stream()
                .filter(r -> targetIds.contains(r.getUserId()))
                .collect(Collectors.toMap(AttendanceRecord::getUserId, r -> r));
        Set<Long> pendingRequestUserIds = new HashSet<>(
                changeRequestRepository.findByStatusAndRequesterIdInAndTargetDate(
                                ChangeRequestStatus.PENDING, targetIds, date)
                        .stream().map(com.attendance.attendance.domain.AttendanceChangeRequest::getRequesterId).toList());

        // 일괄등록(엑셀)으로 승인된 휴가는 attendance_records에 자동 반영되지 않으므로,
        // 근태 기록과 별개로 leave_requests에서 그날과 겹치는 승인된 휴가를 직접 확인해서
        // 연차/반차/반반차/병가/공가/대체휴가 중 어떤 유형인지 정확한 명칭으로 표시한다.
        Instant dayStart = date.atStartOfDay(AppConfig.SEOUL).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(AppConfig.SEOUL).toInstant();
        Map<Long, String> leaveLabelByUser = leaveRequestRepository
                .findByStatusAndRequesterIdInAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
                        ChangeRequestStatus.APPROVED, targetIds, dayEnd, dayStart)
                .stream()
                .filter(l -> LEAVE_BOARD_TYPE_LABELS.containsKey(l.getRequestType()))
                .collect(Collectors.toMap(
                        com.attendance.leave.domain.LeaveRequest::getRequesterId,
                        l -> LEAVE_BOARD_TYPE_LABELS.get(l.getRequestType()),
                        (existing, replacement) -> existing));

        Map<Long, Workplace> workplaceById = workplaceRepository.findAll().stream()
                .collect(Collectors.toMap(Workplace::getId, w -> w, (a, b) -> a));

        // 직원마다 근무제·배정 근무지를 개별 조회하면 대상 인원 수만큼 DB 왕복이 반복되므로,
        // 대상 전체를 각각 한 번의 배치 조회로 미리 구해 메모리에서 매칭한다.
        Map<Long, WorkSchedule> scheduleByUser = workScheduleService.resolveSchedules(targetIds, date);
        Set<LocalDate> holidayDates = new HashSet<>(holidayRepository.findHolidayDatesBetween(date, date));
        Map<Long, Long> assignedWorkplaceIdByUser = new HashMap<>();
        for (Object[] row : workplaceRepository.findAssignedWorkplacesByUserIdsAndDate(targetIds, date)) {
            Long userId = (Long) row[0];
            Workplace workplace = (Workplace) row[1];
            assignedWorkplaceIdByUser.putIfAbsent(userId, workplace.getId());
        }

        return targets.stream()
                .map(user -> {
                    AttendanceRecord record = recordByUser.get(user.getId());
                    WorkSchedule schedule = scheduleByUser.get(user.getId());
                    boolean late = record != null && computeLate(record, schedule, holidayDates);
                    boolean earlyLeave = record != null && computeEarlyLeave(record, schedule);

                    // 근태 기록에 이미 저장된 근무지가 있으면 그 값을 우선하고(예: 실제 GPS 출퇴근 결과),
                    // 없으면 이 직원에게 그날 배정된 근무지로 보정 시 저장할 근무지를 채운다.
                    Long recordWorkplaceId = record != null ? record.getWorkplaceId() : null;
                    Long effectiveWorkplaceId = recordWorkplaceId != null ? recordWorkplaceId
                            : assignedWorkplaceIdByUser.get(user.getId());
                    String effectiveWorkplaceName = effectiveWorkplaceId != null
                            ? Optional.ofNullable(workplaceById.get(effectiveWorkplaceId)).map(Workplace::getName).orElse(null)
                            : null;

                    return AdminAttendanceBoardRow.of(user, record, schedule, late, earlyLeave,
                            pendingRequestUserIds.contains(user.getId()), leaveLabelByUser.get(user.getId()),
                            effectiveWorkplaceId, effectiveWorkplaceName);
                })
                .toList();
    }

    /**
     * 관리자 근태 수동 등록. 해당 일자에 기록이 이미 있으면 거부하고, 감사 로그를 남긴다.
     * HR_ADMIN/SYSTEM_ADMIN은 전체 대상 등록이 가능하고, 그 외에는 파트장 이상 레벨이면서
     * 대상 직원이 본인 조직 산하(출근부 지정일 화면 등)일 때만 허용한다.
     */
    @Transactional
    public AdminAttendanceResponse createManualAttendance(Long actorId, String actorEmail,
                                                           AdminManualAttendanceRequest req) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.USER_NOT_FOUND));
        boolean topAdmin = actor.getRole() == UserRole.HR_ADMIN || actor.getRole() == UserRole.SYSTEM_ADMIN;
        if (!topAdmin) {
            if (!organizationScopeService.isPartLeadOrAbove(actor.getLevel())) {
                throw new AttendanceException(ErrorCode.ACCESS_DENIED);
            }
            Set<Long> managedUserIds = organizationScopeService.resolveManagedUserIds(actorId);
            if (managedUserIds != null && !managedUserIds.contains(req.getUserId())) {
                throw new AttendanceException(ErrorCode.ACCESS_DENIED);
            }
        }
        userRepository.findById(req.getUserId())
                .orElseThrow(() -> new AttendanceException(ErrorCode.USER_NOT_FOUND));
        if (req.getWorkplaceId() != null) {
            workplaceRepository.findById(req.getWorkplaceId())
                    .orElseThrow(() -> new AttendanceException(ErrorCode.WORKPLACE_NOT_FOUND));
        }
        if (recordRepository.existsByUserIdAndWorkDate(req.getUserId(), req.getWorkDate())) {
            throw new AttendanceException(ErrorCode.ATTENDANCE_ALREADY_EXISTS);
        }

        WorkSchedule manualSchedule = workScheduleService.resolveSchedule(req.getUserId(), req.getWorkDate());
        boolean manualLate = scheduleEvaluator.isLate(req.getCheckInAt(), req.getWorkDate(), manualSchedule);
        boolean manualEarlyLeave = scheduleEvaluator.isEarlyLeave(req.getCheckOutAt(), manualSchedule);

        Integer[] manualWorkAndBreak = resolveWorkAndBreakMinutes(
                req.getCheckInAt(), req.getCheckOutAt(), req.getWorkMinutes(), req.getBreakMinutes());
        Integer manualOvertimeMinutes = resolveOvertimeMinutes(
                req.getCheckInAt(), req.getCheckOutAt(), req.getWorkDate(), manualSchedule, req.getOvertimeMinutes());

        AttendanceRecord record = recordRepository.save(AttendanceRecord.createManual(
                req.getUserId(), req.getWorkDate(), req.getWorkplaceId(),
                req.getCheckInAt(), req.getCheckOutAt(), req.getStatus(),
                manualWorkAndBreak[0], manualWorkAndBreak[1], manualOvertimeMinutes,
                manualLate, manualEarlyLeave));

        auditLogService.record(actorId, actorEmail, "ATTENDANCE_MANUAL_CREATED", "ATTENDANCE_RECORD",
                record.getId(), Map.of(
                        "targetUserId", req.getUserId(),
                        "workDate", req.getWorkDate().toString(),
                        "status", req.getStatus().name(),
                        "reason", req.getReason()));

        return toAdminAttendanceResponse(record);
    }

    /**
     * 관리자 근태 보정. 마감된 근태는 수정할 수 없다.
     * HR_ADMIN/SYSTEM_ADMIN은 전체 대상 보정이 가능하고, 그 외에는 파트장 이상 레벨이면서
     * 대상 직원이 본인 조직 산하(출근부 지정일 화면 등)일 때만 허용한다.
     */
    @Transactional
    public AdminAttendanceResponse correctAttendance(Long actorId, String actorEmail, Long recordId,
                                                      AdminAttendanceCorrectionRequest req) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.USER_NOT_FOUND));
        AttendanceRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.ATTENDANCE_RECORD_NOT_FOUND));

        boolean topAdmin = actor.getRole() == UserRole.HR_ADMIN || actor.getRole() == UserRole.SYSTEM_ADMIN;
        if (!topAdmin) {
            if (!organizationScopeService.isPartLeadOrAbove(actor.getLevel())) {
                throw new AttendanceException(ErrorCode.ACCESS_DENIED);
            }
            Set<Long> managedUserIds = organizationScopeService.resolveManagedUserIds(actorId);
            if (managedUserIds != null && !managedUserIds.contains(record.getUserId())) {
                throw new AttendanceException(ErrorCode.ACCESS_DENIED);
            }
        }
        if (record.isClosed()) {
            throw new AttendanceException(ErrorCode.ATTENDANCE_CLOSED);
        }
        if (req.getWorkplaceId() != null) {
            workplaceRepository.findById(req.getWorkplaceId())
                    .orElseThrow(() -> new AttendanceException(ErrorCode.WORKPLACE_NOT_FOUND));
        }

        Map<String, Object> before = Map.of(
                "checkInAt", String.valueOf(record.getCheckInAt()),
                "checkOutAt", String.valueOf(record.getCheckOutAt()),
                "status", record.getStatus().name());

        // 출근·퇴근 시각 중 하나라도 바뀌면 잔업시간도 그 근무제 기준으로 다시 계산해야 하므로,
        // 지각·조퇴 판정에 쓸 근무제를 한 번만 조회해 그대로 재사용한다.
        WorkSchedule schedule = null;
        if (req.getCheckInAt() != null || req.getCheckOutAt() != null) {
            schedule = workScheduleService.resolveSchedule(record.getUserId(), record.getWorkDate());
        }
        Boolean late = req.getCheckInAt() != null
                ? scheduleEvaluator.isLate(req.getCheckInAt(), record.getWorkDate(), schedule) : null;
        Boolean earlyLeave = req.getCheckOutAt() != null
                ? scheduleEvaluator.isEarlyLeave(req.getCheckOutAt(), schedule) : null;

        // 출근부(지정일) 화면처럼 근태상태를 직접 고르지 않고 시각만 입력·저장하는 경로에서는
        // 퇴근/조퇴 등 상태값이 시각 변경에 맞춰 저절로 갱신되어야 한다. 관리자가 상태를 명시적으로
        // 선택한 경우(req.getStatus() != null, 근태 보정 모달 등)는 그 선택을 그대로 우선한다.
        AttendanceStatus statusToApply = req.getStatus();
        if (statusToApply == null) {
            if (req.getCheckOutAt() != null) {
                statusToApply = Boolean.TRUE.equals(earlyLeave) ? AttendanceStatus.EARLY_LEAVE : AttendanceStatus.FINISHED;
            } else if (req.getCheckInAt() != null
                    && (record.getStatus() == AttendanceStatus.WORKING || record.getStatus() == AttendanceStatus.LATE)) {
                statusToApply = Boolean.TRUE.equals(late) ? AttendanceStatus.LATE : AttendanceStatus.WORKING;
            }
        }

        // 출근·퇴근 시각 보정 시 관리자가 근무(분)을 직접 입력하지 않았다면, 보정 후 최종 시각
        // 기준으로 휴게시간(기본 1시간)을 제외한 근무시간을 서버가 계산해 저장한다.
        Instant effectiveCheckIn = req.getCheckInAt() != null ? req.getCheckInAt() : record.getCheckInAt();
        Instant effectiveCheckOut = req.getCheckOutAt() != null ? req.getCheckOutAt() : record.getCheckOutAt();
        Integer[] workAndBreak = resolveWorkAndBreakMinutes(
                effectiveCheckIn, effectiveCheckOut, req.getWorkMinutes(), req.getBreakMinutes());
        // 잔업시간도 위와 같은 이유로, 관리자가 직접 입력하지 않았다면 보정 후 최종 시각과 근무제 기준으로
        // "근무스케줄 외 근무시간"과 동일하게 다시 계산해 저장한다(그렇지 않으면 퇴근시각만 보정해도
        // 예전 잔업시간이 그대로 남아 있게 된다).
        Integer overtimeMinutes = resolveOvertimeMinutes(
                effectiveCheckIn, effectiveCheckOut, record.getWorkDate(), schedule, req.getOvertimeMinutes());

        record.applyAdminCorrection(req.getCheckInAt(), req.getCheckOutAt(), req.getWorkplaceId(),
                statusToApply, workAndBreak[0], workAndBreak[1], overtimeMinutes,
                late, earlyLeave);

        auditLogService.record(actorId, actorEmail, "ATTENDANCE_CORRECTED", "ATTENDANCE_RECORD", recordId,
                Map.of("before", before, "reason", req.getReason()));

        notificationService.notify(record.getUserId(), NotificationType.ATTENDANCE_CORRECTED,
                "근태 기록이 관리자에 의해 보정되었습니다.",
                record.getWorkDate() + " 근태 기록이 보정되었습니다. (" + req.getReason() + ")",
                "ATTENDANCE_RECORD", recordId);

        return toAdminAttendanceResponse(record);
    }

    /**
     * 지정한 월의 근태를 마감한다. 이후 해당 월의 출퇴근·보정·수정요청 승인은 차단된다.
     */
    @Transactional
    public int closeMonth(Long actorId, String actorEmail, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<AttendanceRecord> records = recordRepository.findByWorkDateBetweenAllUsers(ym.atDay(1), ym.atEndOfMonth());
        records.forEach(AttendanceRecord::close);

        auditLogService.record(actorId, actorEmail, "ATTENDANCE_MONTH_CLOSED", "ATTENDANCE_MONTH",
                null, Map.of("year", year, "month", month, "recordCount", records.size()));

        records.forEach(r -> notificationService.notify(r.getUserId(), NotificationType.ATTENDANCE_CLOSED,
                year + "년 " + month + "월 근태가 마감되었습니다.",
                "해당 월의 근태 기록은 이제 수정할 수 없습니다.", "ATTENDANCE_MONTH", null));

        return records.size();
    }

    /**
     * 마감된 월을 재오픈한다.
     */
    @Transactional
    public int reopenMonth(Long actorId, String actorEmail, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<AttendanceRecord> records = recordRepository.findByWorkDateBetweenAllUsers(ym.atDay(1), ym.atEndOfMonth());
        records.forEach(AttendanceRecord::reopen);

        auditLogService.record(actorId, actorEmail, "ATTENDANCE_MONTH_REOPENED", "ATTENDANCE_MONTH",
                null, Map.of("year", year, "month", month, "recordCount", records.size()));

        return records.size();
    }

    /**
     * 월별 근태 요약을 엑셀(xlsx)로 생성한다.
     */
    @Transactional(readOnly = true)
    public byte[] exportMonthlyExcel(int year, int month, Long actorId) {
        List<AdminMonthlyUserSummary> summaries = getMonthlySummary(year, month, actorId);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(year + "-" + String.format("%02d", month));

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            String[] headers = {"사번", "이름", "근무일수", "정상출근", "지각", "조퇴", "결근", "총근무(분)", "연장근무(분)"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (AdminMonthlyUserSummary summary : summaries) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(summary.getEmployeeNumber() == null ? "" : summary.getEmployeeNumber());
                row.createCell(1).setCellValue(summary.getUserName());
                row.createCell(2).setCellValue(summary.getWorkingDays());
                row.createCell(3).setCellValue(summary.getPresentDays());
                row.createCell(4).setCellValue(summary.getLateDays());
                row.createCell(5).setCellValue(summary.getEarlyLeaveDays());
                row.createCell(6).setCellValue(summary.getAbsentDays());
                row.createCell(7).setCellValue(summary.getTotalWorkMinutes());
                row.createCell(8).setCellValue(summary.getTotalOvertimeMinutes());
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("엑셀 파일 생성 중 오류가 발생했습니다.", e);
        }
    }

    private AdminAttendanceResponse toAdminAttendanceResponse(AttendanceRecord record) {
        String userName = userRepository.findById(record.getUserId()).map(User::getName).orElse("(삭제됨)");
        String empNumber = userRepository.findById(record.getUserId()).map(User::getEmployeeNumber).orElse("");
        String workplaceName = record.getWorkplaceId() == null ? null :
                workplaceRepository.findById(record.getWorkplaceId()).map(Workplace::getName).orElse(null);
        return AdminAttendanceResponse.from(record, userName, empNumber, workplaceName,
                computeLate(record), computeEarlyLeave(record));
    }
}
