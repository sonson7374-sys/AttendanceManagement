package com.attendance.common.exception;

import com.attendance.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AttendanceException.class)
    public ResponseEntity<ApiResponse<Void>> handleAttendanceException(AttendanceException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("AttendanceException: {} - {}", code.getCode(), e.getMessage());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        ErrorCode code = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getCode(), message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        ErrorCode code = ErrorCode.ACCESS_DENIED;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getCode(), code.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException e) {
        ErrorCode code = ErrorCode.INVALID_CREDENTIALS;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getCode(), code.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        ErrorCode code = ErrorCode.OPTIMISTIC_LOCK_CONFLICT;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getCode(), code.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("DataIntegrityViolation: {}", e.getMostSpecificCause().getMessage());
        ErrorCode code = ErrorCode.INVALID_INPUT;
        String message = parseConstraintMessage(e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getCode(), message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadable: {}", e.getMessage());
        ErrorCode code = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getCode(), "요청 본문 형식이 올바르지 않습니다."));
    }

    private String parseConstraintMessage(String cause) {
        if (cause == null) return "입력값이 올바르지 않습니다.";
        if (cause.contains("chk_work_times")) return "근무 종료 시각은 시작 시각보다 늦어야 합니다.";
        if (cause.contains("chk_required_minutes")) return "소정 근무시간은 1분 이상 720분 이하여야 합니다.";
        if (cause.contains("work_schedules_name")) return "이미 사용 중인 근무제명입니다.";
        if (cause.contains("foreign key") || cause.contains("violates foreign key")) return "참조 데이터가 존재하지 않습니다.";
        return "데이터 제약 조건을 위반했습니다.";
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getCode(), code.getMessage()));
    }
}
