package com.attendance.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "geofence")
public class GeofenceProperties {
    private int defaultRadiusMeters = 100;
    private int maxAccuracyMeters = 50;
    private int maxLocationAgeSeconds = 30;
    private boolean allowMockLocation = false;
    private int retryCount = 3;
    private boolean allowOutsideCheckIn = false;
    private boolean allowOutsideCheckOut = false;
}
