package com.attendance.common.exception;

import lombok.Getter;

@Getter
public class AttendanceException extends RuntimeException {

    private final ErrorCode errorCode;

    public AttendanceException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AttendanceException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }
}
