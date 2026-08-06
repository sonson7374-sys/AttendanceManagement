package com.attendance.calendar.domain;

public enum CalendarEventVisibility {
    /** 로그인한 모든 사용자에게 보이는 전체 공유 일정. */
    ALL,
    /** targetUserId로 지정된 직원 본인에게만 보이는 개인 일정. */
    PERSONAL
}
