package com.attendance.attendance.dto;

import com.attendance.attendance.domain.ChangeRequestType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
public class ChangeRequestSubmitRequest {

    @NotNull
    private Long recordId;

    @NotNull
    private ChangeRequestType changeType;

    private OffsetDateTime requestedCheckIn;
    private OffsetDateTime requestedCheckOut;
    private Long requestedWorkplaceId;

    @NotNull
    private String reason;
}
