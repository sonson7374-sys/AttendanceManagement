package com.attendance.geofence.service;

import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.config.GeofenceProperties;
import com.attendance.workplace.domain.Workplace;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GeofenceService {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    /**
     * 두 GPS 이벤트 사이의 함의 이동속도가 이 값을 넘으면 모의 위치(GPS 조작)로 간주한다.
     * 국내 이동 수단(항공 포함) 기준 여유를 둔 임계값이다.
     */
    private static final double MAX_PLAUSIBLE_SPEED_KMH = 800.0;
    private static final long MIN_INTERVAL_SECONDS_FOR_CHECK = 5;

    private final GeofenceProperties properties;
    private final Clock clock;

    /**
     * GPS 입력값을 검증한다.
     * @param latitude   위도
     * @param longitude  경도
     * @param accuracyM  정확도(미터)
     * @param capturedAt GPS 측정 시각
     * @param mockDetected 모의 위치 여부
     */
    public void validateGpsInput(double latitude, double longitude,
                                  double accuracyM, Instant capturedAt,
                                  boolean mockDetected) {
        if (!properties.isAllowMockLocation() && mockDetected) {
            throw new AttendanceException(ErrorCode.MOCK_LOCATION_DETECTED);
        }
        if (accuracyM > properties.getMaxAccuracyMeters()) {
            throw new AttendanceException(ErrorCode.LOW_LOCATION_ACCURACY,
                    String.format("위치 정확도 %.1fm 초과 (허용: %dm)", accuracyM, properties.getMaxAccuracyMeters()));
        }
        long ageSeconds = Instant.now(clock).getEpochSecond() - capturedAt.getEpochSecond();
        if (ageSeconds > properties.getMaxLocationAgeSeconds()) {
            throw new AttendanceException(ErrorCode.LOCATION_TOO_OLD,
                    String.format("GPS 좌표가 %d초 전 측정됨 (허용: %d초)", ageSeconds, properties.getMaxLocationAgeSeconds()));
        }
    }

    /**
     * 직전 GPS 이벤트와 현재 요청 좌표 사이의 함의 이동속도를 계산해 물리적으로 불가능하면
     * 모의 위치(GPS 조작)로 판정한다. 클라이언트가 보낸 mockLocationDetected 값을 신뢰하지 않고
     * 서버가 독립적으로 검증하기 위한 보조 수단이다.
     */
    public void validateMovementPlausibility(BigDecimal lastLatitude, BigDecimal lastLongitude, Instant lastEventAt,
                                              double latitude, double longitude, Instant capturedAt) {
        if (lastLatitude == null || lastLongitude == null || lastEventAt == null) {
            return;
        }
        long intervalSeconds = Duration.between(lastEventAt, capturedAt).getSeconds();
        if (intervalSeconds < MIN_INTERVAL_SECONDS_FOR_CHECK) {
            return;
        }
        double distanceMeters = calculateDistance(
                lastLatitude.doubleValue(), lastLongitude.doubleValue(), latitude, longitude);
        double speedKmh = (distanceMeters / 1000.0) / (intervalSeconds / 3600.0);
        if (speedKmh > MAX_PLAUSIBLE_SPEED_KMH) {
            throw new AttendanceException(ErrorCode.MOCK_LOCATION_DETECTED,
                    String.format("직전 위치 대비 이동속도 %.0fkm/h로 비정상적입니다.", speedKmh));
        }
    }

    /**
     * 할당된 근무지 목록에서 가장 가까운 근무지와 거리를 반환한다.
     * 반환된 결과의 withinGeofence()가 false이면 범위 밖이다.
     */
    public WorkplaceDistance findClosest(List<Workplace> workplaces, double latitude, double longitude) {
        if (workplaces.isEmpty()) {
            throw new AttendanceException(ErrorCode.NO_ASSIGNED_WORKPLACE);
        }
        return workplaces.stream()
                .map(wp -> new WorkplaceDistance(wp, calculateDistance(
                        latitude, longitude,
                        wp.getLatitude().doubleValue(), wp.getLongitude().doubleValue())))
                .min(Comparator.comparingDouble(WorkplaceDistance::getDistanceMeters))
                .orElseThrow(() -> new AttendanceException(ErrorCode.NO_ASSIGNED_WORKPLACE));
    }

    /**
     * Haversine 공식으로 두 좌표 간 거리를 미터 단위로 계산한다.
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_METERS * c;
    }

    @Getter
    public static class WorkplaceDistance {
        private final Workplace workplace;
        private final double distanceMeters;

        public WorkplaceDistance(Workplace workplace, double distanceMeters) {
            this.workplace = workplace;
            this.distanceMeters = distanceMeters;
        }

        public boolean isWithinGeofence() {
            return distanceMeters <= workplace.getRadiusMeters();
        }
    }
}
