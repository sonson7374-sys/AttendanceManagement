package com.attendance.organization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class OrganizationRequest {
    @NotBlank
    private String name;
    private Long parentId;
    private Integer displayOrder;
}
