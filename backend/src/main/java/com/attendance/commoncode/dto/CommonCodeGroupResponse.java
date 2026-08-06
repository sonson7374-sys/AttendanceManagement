package com.attendance.commoncode.dto;

import com.attendance.commoncode.domain.CommonCodeGroup;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommonCodeGroupResponse {
    private Long id;
    private String groupCode;
    private String groupName;
    private String description;
    private boolean protectedGroup;

    public static CommonCodeGroupResponse from(CommonCodeGroup g) {
        return CommonCodeGroupResponse.builder()
                .id(g.getId())
                .groupCode(g.getGroupCode())
                .groupName(g.getGroupName())
                .description(g.getDescription())
                .protectedGroup(g.isProtectedGroup())
                .build();
    }
}
