package com.attendance.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "attendance_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "record_id")
    private Long recordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private EventType eventType;

    @Column(name = "event_at", nullable = false)
    private Instant eventAt;

    @Column(name = "workplace_id")
    private Long workplaceId;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "accuracy_meters", precision = 6, scale = 2)
    private BigDecimal accuracyMeters;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "device_platform", length = 20)
    private String devicePlatform;

    @Column(name = "mock_detected", nullable = false)
    private boolean mockDetected;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public AttendanceEvent(Long userId, Long recordId, EventType eventType, Instant eventAt,
                            Long workplaceId, BigDecimal latitude, BigDecimal longitude,
                            BigDecimal accuracyMeters, Integer distanceMeters,
                            String deviceId, String devicePlatform, boolean mockDetected) {
        this.userId = userId;
        this.recordId = recordId;
        this.eventType = eventType;
        this.eventAt = eventAt;
        this.workplaceId = workplaceId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.distanceMeters = distanceMeters;
        this.deviceId = deviceId;
        this.devicePlatform = devicePlatform;
        this.mockDetected = mockDetected;
    }
}
