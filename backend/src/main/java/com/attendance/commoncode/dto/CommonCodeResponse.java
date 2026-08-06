package com.attendance.commoncode.dto;

import com.attendance.commoncode.domain.CommonCode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommonCodeResponse {
    private Long id;
    private String groupCode;
    private String code;
    private String codeName;
    private String description;
    private int displayOrder;
    private boolean active;
    private boolean protectedCode;

    public static CommonCodeResponse from(CommonCode c) {
        return CommonCodeResponse.builder()
                .id(c.getId())
                .groupCode(c.getGroupCode())
                .code(c.getCode())
                .codeName(c.getCodeName())
                .description(c.getDescription())
                .displayOrder(c.getDisplayOrder())
                .active(c.isActive())
                .protectedCode(c.isProtectedCode())
                .build();
    }
}
