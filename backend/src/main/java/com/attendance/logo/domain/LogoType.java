package com.attendance.logo.domain;

import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;

import java.util.Locale;

// 로고가 쓰이는 위치별로 독립적으로 관리한다: 로그인 화면(관리자웹 로그인 페이지 + 모바일 앱 로그인
// 화면)과 관리자웹 로그인 후 좌측 메뉴 하단. 인증 전/후 화면이라 노출 맥락이 달라 같은 이미지를
// 강제할 필요가 없다.
public enum LogoType {
    LOGIN,
    SIDEBAR;

    public String slug() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static LogoType fromSlug(String value) {
        for (LogoType type : values()) {
            if (type.slug().equals(value)) {
                return type;
            }
        }
        throw new AttendanceException(ErrorCode.INVALID_INPUT, "존재하지 않는 로고 종류입니다.");
    }
}
