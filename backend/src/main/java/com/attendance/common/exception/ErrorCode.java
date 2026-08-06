package com.attendance.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Auth
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_001", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_002", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "만료된 토큰입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_004", "접근 권한이 없습니다."),
    ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED, "AUTH_005", "잠긴 계정입니다. 관리자에게 문의하세요."),
    ACCOUNT_INACTIVE(HttpStatus.UNAUTHORIZED, "AUTH_006", "비활성 계정입니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "사용자를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_002", "이미 사용 중인 이메일입니다."),
    EMPLOYEE_NUMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_003", "이미 사용 중인 사번입니다."),

    // Company / Organization
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "ORG_001", "회사를 찾을 수 없습니다."),
    ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND, "ORG_002", "부서를 찾을 수 없습니다."),

    // Workplace
    WORKPLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "WP_001", "근무지를 찾을 수 없습니다."),
    NO_ASSIGNED_WORKPLACE(HttpStatus.UNPROCESSABLE_ENTITY, "WP_002", "배정된 근무지가 없습니다."),
    WORKPLACE_CHECK_IN_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "WP_003", "해당 근무지는 출근 처리가 허용되지 않습니다."),
    WORKPLACE_CHECK_OUT_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "WP_004", "해당 근무지는 퇴근 처리가 허용되지 않습니다."),
    WORKPLACE_CHANGE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "WP_005", "근무지 변경 요청을 찾을 수 없습니다."),
    WORKPLACE_CHANGE_REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "WP_006", "이미 처리된 근무지 변경 요청입니다."),

    // Work Schedule
    WORK_SCHEDULE_CHANGE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "WS_001", "근무제 변경 요청을 찾을 수 없습니다."),
    WORK_SCHEDULE_CHANGE_REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "WS_002", "이미 처리된 근무제 변경 요청입니다."),

    // Attendance
    ALREADY_CHECKED_IN(HttpStatus.CONFLICT, "ATT_001", "이미 출근 처리되었습니다."),
    ALREADY_CHECKED_OUT(HttpStatus.CONFLICT, "ATT_002", "이미 퇴근 처리되었습니다."),
    NOT_CHECKED_IN(HttpStatus.UNPROCESSABLE_ENTITY, "ATT_003", "출근 기록이 없습니다."),
    OUTSIDE_GEOFENCE(HttpStatus.UNPROCESSABLE_ENTITY, "ATT_004", "허용 근무지 범위 밖입니다."),
    LOW_LOCATION_ACCURACY(HttpStatus.UNPROCESSABLE_ENTITY, "ATT_005", "위치 정확도가 부족합니다."),
    LOCATION_TOO_OLD(HttpStatus.UNPROCESSABLE_ENTITY, "ATT_006", "GPS 좌표가 오래되었습니다."),
    MOCK_LOCATION_DETECTED(HttpStatus.UNPROCESSABLE_ENTITY, "ATT_007", "모의 위치가 감지되었습니다."),
    BREAK_NOT_ENDED(HttpStatus.UNPROCESSABLE_ENTITY, "ATT_008", "종료되지 않은 휴게시간이 있습니다."),
    ATTENDANCE_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "ATT_009", "마감된 근태 기록입니다."),
    CHANGE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "ATT_010", "수정 요청을 찾을 수 없습니다."),
    CHANGE_REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "ATT_011", "이미 처리된 수정 요청입니다."),
    CHANGE_REQUEST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ATT_012", "해당 수정 요청에 대한 권한이 없습니다."),
    ATTENDANCE_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "ATT_013", "근태 기록을 찾을 수 없습니다."),
    BREAK_ALREADY_STARTED(HttpStatus.CONFLICT, "ATT_014", "이미 휴게 중입니다."),
    NOT_ON_BREAK(HttpStatus.UNPROCESSABLE_ENTITY, "ATT_015", "휴게 중이 아닙니다."),
    CANNOT_START_BREAK(HttpStatus.UNPROCESSABLE_ENTITY, "ATT_016", "근무 중 상태에서만 휴게를 시작할 수 있습니다."),
    ATTENDANCE_ALREADY_EXISTS(HttpStatus.CONFLICT, "ATT_017", "해당 일자의 근태 기록이 이미 존재합니다."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "ATT_018", "이미 처리 중인 요청입니다. 잠시 후 다시 시도해주세요."),

    // Leave / Outside-work requests
    LEAVE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "LEAVE_001", "휴가 신청을 찾을 수 없습니다."),
    LEAVE_REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "LEAVE_002", "이미 처리된 휴가 신청입니다."),
    LEAVE_REQUEST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "LEAVE_003", "해당 휴가 신청에 대한 권한이 없습니다."),
    OUTSIDE_WORK_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "OUT_001", "외근·출장 신청을 찾을 수 없습니다."),
    OUTSIDE_WORK_REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "OUT_002", "이미 처리된 외근·출장 신청입니다."),
    OUTSIDE_WORK_REQUEST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "OUT_003", "해당 외근·출장 신청에 대한 권한이 없습니다."),

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "입력값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_002", "리소스를 찾을 수 없습니다."),
    OPTIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT, "COMMON_003", "동시 수정이 감지되었습니다. 다시 시도해주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
