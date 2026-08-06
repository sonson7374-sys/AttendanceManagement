package com.attendance.user.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_devices", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "device_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(name = "device_platform", nullable = false, length = 20)
    private String devicePlatform;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Column(name = "fcm_token", length = 500)
    private String fcmToken;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Builder
    public UserDevice(Long userId, String deviceId, String devicePlatform, String deviceName,
                       String fcmToken, Instant registeredAt) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.devicePlatform = devicePlatform;
        this.deviceName = deviceName;
        this.fcmToken = fcmToken;
        this.active = true;
        this.registeredAt = registeredAt;
        this.lastSeenAt = registeredAt;
    }

    public void touch(Instant now, String fcmToken) {
        this.lastSeenAt = now;
        if (fcmToken != null) {
            this.fcmToken = fcmToken;
        }
    }

    public void revoke() {
        this.active = false;
    }
}
