package com.attendance.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "privacy")
public class PrivacyProperties {
    private int locationRetentionDays = 90;
    private int auditLogRetentionDays = 365;
}
