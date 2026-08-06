package com.attendance.commoncode.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommonCodeUpdateRequest {

    @NotBlank
    private String codeName;

    private String description;

    private int displayOrder;

    private boolean active;
}
