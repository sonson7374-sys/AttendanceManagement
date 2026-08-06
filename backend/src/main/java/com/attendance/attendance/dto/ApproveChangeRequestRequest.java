package com.attendance.attendance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApproveChangeRequestRequest {

    @NotNull
    private String action; // APPROVE or REJECT

    private String comment;
}
