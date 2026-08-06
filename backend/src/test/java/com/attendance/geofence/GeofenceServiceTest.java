package com.attendance.geofence;

import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.config.GeofenceProperties;
import com.attendance.geofence.service.GeofenceService;
import com.attendance.workplace.domain.Workplace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class GeofenceServiceTest {

    private GeofenceService geofenceService;
    private GeofenceProperties properties;

    // 서울 본사 좌표 (테스트 고정값)
    private static final double WORKPLACE_LAT = 37.566500;
    private static final double WORKPLACE_LON = 126.978000;

    @BeforeEach
    void setUp() {
        properties = new GeofenceProperties();
        properties.setMaxAccuracyMeters(50);
        properties.setMaxLocationAgeSeconds(30);
        properties.setAllowMockLocation(false);
        properties.setDefaultRadiusMeters(100);
        geofenceService = new GeofenceService(properties, Clock.systemUTC());
    }

    // ─── Haversine 거리 계산 ─────────────────────────────

    @Test
    @DisplayName("동일 좌표는 거리 0")
    void samePoint_zeroDistance() {
        double dist = geofenceService.calculateDistance(
                WORKPLACE_LAT, WORKPLACE_LON, WORKPLACE_LAT, WORKPLACE_LON);
        assertThat(dist).isEqualTo(0.0);
    }

    @Test
    @DisplayName("반경 100m 안쪽 좌표는 withinGeofence=true")
    void insideRadius_withinGeofence() {
        // 80m 북쪽 — 정확한 위도-미터 변환계수(111,320)를 사용해 안전 여유 확보
        double nearLat = WORKPLACE_LAT + (80.0 / 111_320.0);
        GeofenceService.WorkplaceDistance result = geofenceService.findClosest(
                List.of(buildWorkplace(100)), nearLat, WORKPLACE_LON);
        assertThat(result.isWithinGeofence()).isTrue();
        assertThat(result.getDistanceMeters()).isLessThan(100.0);
    }

    @Test
    @DisplayName("반경 100m 바깥 좌표는 withinGeofence=false")
    void outsideRadius_notWithinGeofence() {
        // 120m 북쪽 — 동일 계수로 명확히 경계 밖
        double farLat = WORKPLACE_LAT + (120.0 / 111_320.0);
        GeofenceService.WorkplaceDistance result = geofenceService.findClosest(
                List.of(buildWorkplace(100)), farLat, WORKPLACE_LON);
        assertThat(result.isWithinGeofence()).isFalse();
        assertThat(result.getDistanceMeters()).isGreaterThan(100.0);
    }

    @Test
    @DisplayName("여러 근무지 중 가장 가까운 곳을 선택")
    void multipleWorkplaces_selectsClosest() {
        Workplace far = buildWorkplace(100, 37.5, 126.9);   // 멀리
        Workplace near = buildWorkplace(100, WORKPLACE_LAT, WORKPLACE_LON);  // 동일
        GeofenceService.WorkplaceDistance result = geofenceService.findClosest(
                List.of(far, near), WORKPLACE_LAT, WORKPLACE_LON);
        assertThat(result.getDistanceMeters()).isEqualTo(0.0);
    }

    // ─── GPS 정확도 검증 ─────────────────────────────────

    @Test
    @DisplayName("정확도 50m는 허용")
    void accuracy50m_allowed() {
        Instant now = Instant.now();
        assertThatCode(() -> geofenceService.validateGpsInput(
                WORKPLACE_LAT, WORKPLACE_LON, 50.0, now, false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정확도 50.1m는 LOW_LOCATION_ACCURACY")
    void accuracy50_1m_rejected() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> geofenceService.validateGpsInput(
                WORKPLACE_LAT, WORKPLACE_LON, 50.1, now, false))
                .isInstanceOf(AttendanceException.class)
                .extracting(e -> ((AttendanceException) e).getErrorCode())
                .isEqualTo(ErrorCode.LOW_LOCATION_ACCURACY);
    }

    // ─── GPS 측정 시각 검증 ──────────────────────────────

    @Test
    @DisplayName("30초 된 GPS 좌표는 허용")
    void age30s_allowed() {
        Instant thirtySecondsAgo = Instant.now().minusSeconds(30);
        assertThatCode(() -> geofenceService.validateGpsInput(
                WORKPLACE_LAT, WORKPLACE_LON, 10.0, thirtySecondsAgo, false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("31초 된 GPS 좌표는 LOCATION_TOO_OLD")
    void age31s_rejected() {
        Instant thirtyOneSecondsAgo = Instant.now().minusSeconds(31);
        assertThatThrownBy(() -> geofenceService.validateGpsInput(
                WORKPLACE_LAT, WORKPLACE_LON, 10.0, thirtyOneSecondsAgo, false))
                .isInstanceOf(AttendanceException.class)
                .extracting(e -> ((AttendanceException) e).getErrorCode())
                .isEqualTo(ErrorCode.LOCATION_TOO_OLD);
    }

    // ─── 모의 위치 ───────────────────────────────────────

    @Test
    @DisplayName("모의 위치 감지 시 MOCK_LOCATION_DETECTED")
    void mockLocation_rejected() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> geofenceService.validateGpsInput(
                WORKPLACE_LAT, WORKPLACE_LON, 10.0, now, true))
                .isInstanceOf(AttendanceException.class)
                .extracting(e -> ((AttendanceException) e).getErrorCode())
                .isEqualTo(ErrorCode.MOCK_LOCATION_DETECTED);
    }

    @Test
    @DisplayName("allowMockLocation=true 설정 시 모의 위치 허용")
    void mockLocation_allowedWhenConfigured() {
        properties.setAllowMockLocation(true);
        Instant now = Instant.now();
        assertThatCode(() -> geofenceService.validateGpsInput(
                WORKPLACE_LAT, WORKPLACE_LON, 10.0, now, true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("할당 근무지 없으면 NO_ASSIGNED_WORKPLACE")
    void emptyWorkplaces_throws() {
        assertThatThrownBy(() -> geofenceService.findClosest(
                List.of(), WORKPLACE_LAT, WORKPLACE_LON))
                .isInstanceOf(AttendanceException.class)
                .extracting(e -> ((AttendanceException) e).getErrorCode())
                .isEqualTo(ErrorCode.NO_ASSIGNED_WORKPLACE);
    }

    // ─── 헬퍼 ────────────────────────────────────────────

    private Workplace buildWorkplace(int radius) {
        return buildWorkplace(radius, WORKPLACE_LAT, WORKPLACE_LON);
    }

    private Workplace buildWorkplace(int radius, double lat, double lon) {
        return Workplace.builder()
                .companyId(1L)
                .name("테스트 근무지")
                .latitude(BigDecimal.valueOf(lat))
                .longitude(BigDecimal.valueOf(lon))
                .radiusMeters(radius)
                .build();
    }
}
