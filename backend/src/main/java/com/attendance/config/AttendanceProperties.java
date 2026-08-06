package com.attendance.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "attendance")
public class AttendanceProperties {
    private int duplicateRequestWindowSeconds = 10;
    private boolean allowCrossMidnightCheckout = true;
}
