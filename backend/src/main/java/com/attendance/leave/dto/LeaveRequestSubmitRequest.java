package com.attendance.leave.dto;

import com.attendance.leave.domain.LeaveRequestType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
public class LeaveRequestSubmitRequest {

    @NotNull
    private LeaveRequestType requestType;

    @NotNull
    private OffsetDateTime startAt;

    @NotNull
    private OffsetDateTime endAt;

    @NotNull
    private String reason;
}
