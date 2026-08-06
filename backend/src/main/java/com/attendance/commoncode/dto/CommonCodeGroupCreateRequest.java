package com.attendance.commoncode.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommonCodeGroupCreateRequest {

    @NotBlank
    private String groupCode;

    @NotBlank
    private String groupName;

    private String description;
}
