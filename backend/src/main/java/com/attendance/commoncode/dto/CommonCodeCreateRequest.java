package com.attendance.commoncode.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommonCodeCreateRequest {

    @NotBlank
    private String groupCode;

    @NotBlank
    private String code;

    @NotBlank
    private String codeName;

    private String description;

    private int displayOrder;
}
