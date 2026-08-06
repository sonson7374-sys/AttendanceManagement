package com.attendance.user.dto;

import com.attendance.user.domain.UserDevice;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class UserDeviceResponse {
    private Long id;
    private String deviceId;
    private String devicePlatform;
    private String deviceName;
    private boolean active;
    private Instant registeredAt;
    private Instant lastSeenAt;

    public static UserDeviceResponse from(UserDevice device) {
        return UserDeviceResponse.builder()
                .id(device.getId())
                .deviceId(device.getDeviceId())
                .devicePlatform(device.getDevicePlatform())
                .deviceName(device.getDeviceName())
                .active(device.isActive())
                .registeredAt(device.getRegisteredAt())
                .lastSeenAt(device.getLastSeenAt())
                .build();
    }
}
