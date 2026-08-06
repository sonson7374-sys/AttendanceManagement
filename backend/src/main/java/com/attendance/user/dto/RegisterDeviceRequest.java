package com.attendance.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RegisterDeviceRequest {

    @NotBlank(message = "기기 ID를 입력해주세요.")
    private String deviceId;

    @NotBlank(message = "기기 플랫폼을 입력해주세요.")
    private String devicePlatform;

    private String deviceName;
    private String fcmToken;
}
