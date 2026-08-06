package com.attendance.integration;

import com.attendance.auth.dto.LoginRequest;
import com.attendance.user.domain.User;
import com.attendance.user.domain.UserRole;
import com.attendance.user.repository.UserRepository;
import com.attendance.workplace.domain.Workplace;
import com.attendance.workplace.repository.WorkplaceRepository;
import com.attendance.workplace.domain.UserWorkplace;
import com.attendance.workplace.repository.UserWorkplaceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AttendanceIntegrationTest extends IntegrationTestBase {

    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired WorkplaceRepository workplaceRepository;
    @Autowired UserWorkplaceRepository userWorkplaceRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // 서울 본사 좌표 (위도 37.5665, 경도 126.9780)
    static final double OFFICE_LAT = 37.5665;
    static final double OFFICE_LNG = 126.9780;
    static final double OUTSIDE_LAT = 35.0; // 경남
    static final double OUTSIDE_LNG = 126.0;

    User testEmployee;
    Workplace testWorkplace;
    String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        // 직원 생성
        testEmployee = userRepository.save(User.builder()
                .email("employee@test.com")
                .password(passwordEncoder.encode("Password1!"))
                .name("테스트직원")
                .employeeNumber("EMP-001")
                .companyId(1L)
                .role(UserRole.EMPLOYEE)
                .build());

        // 근무지 생성 (반경 500m)
        testWorkplace = workplaceRepository.save(Workplace.builder()
                .companyId(1L)
                .name("서울 사무소")
                .address("서울시 중구")
                .latitude(new java.math.BigDecimal("37.5665"))
                .longitude(new java.math.BigDecimal("126.9780"))
                .radiusMeters(500)
                .build());

        // 근무지 배정
        userWorkplaceRepository.save(UserWorkplace.builder()
                .userId(testEmployee.getId())
                .workplaceId(testWorkplace.getId())
                .build());

        // 로그인하여 토큰 취득
        accessToken = obtainAccessToken("employee@test.com", "Password1!");
    }

    private String obtainAccessToken(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(req, "email", email);
        org.springframework.test.util.ReflectionTestUtils.setField(req, "password", password);

        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        var tree = objectMapper.readTree(result.getResponse().getContentAsString());
        return tree.at("/data/accessToken").asText();
    }

    private Map<String, Object> checkInBody(double lat, double lng) {
        return Map.of(
                "latitude", lat,
                "longitude", lng,
                "accuracyMeters", 5.0,
                "capturedAt", OffsetDateTime.now(ZoneOffset.UTC).toString(),
                "mockLocationDetected", false
        );
    }

    // ─── 출근 ────────────────────────────────────────────────

    @Test
    @DisplayName("정상 출근 처리 → 200 OK, status=WORKING")
    void checkIn_success() throws Exception {
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkInBody(OFFICE_LAT, OFFICE_LNG))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("WORKING"));
    }

    @Test
    @DisplayName("허용 반경 외부에서 출근 시도 → 422 OUTSIDE_GEOFENCE")
    void checkIn_outsideGeofence() throws Exception {
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkInBody(OUTSIDE_LAT, OUTSIDE_LNG))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ATT_004"));
    }

    @Test
    @DisplayName("중복 출근 시도 → 409 ALREADY_CHECKED_IN")
    void checkIn_duplicate() throws Exception {
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkInBody(OFFICE_LAT, OFFICE_LNG))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkInBody(OFFICE_LAT, OFFICE_LNG))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATT_001"));
    }

    // ─── 퇴근 ────────────────────────────────────────────────

    @Test
    @DisplayName("정상 출퇴근 처리 → 퇴근 후 status=FINISHED")
    void checkOut_success() throws Exception {
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkInBody(OFFICE_LAT, OFFICE_LNG))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/attendance/check-out")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkInBody(OFFICE_LAT, OFFICE_LNG))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINISHED"));
    }

    @Test
    @DisplayName("출근 없이 퇴근 시도 → 422 NOT_CHECKED_IN")
    void checkOut_withoutCheckIn() throws Exception {
        mockMvc.perform(post("/api/v1/attendance/check-out")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkInBody(OFFICE_LAT, OFFICE_LNG))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ATT_003"));
    }

    // ─── 오늘 근태 조회 ───────────────────────────────────────

    @Test
    @DisplayName("출근 전 오늘 근태 조회 → status=BEFORE_WORK")
    void today_beforeWork() throws Exception {
        mockMvc.perform(get("/api/v1/attendance/today")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BEFORE_WORK"));
    }

    // ─── 인증 ─────────────────────────────────────────────────

    @Test
    @DisplayName("인증 없이 출근 요청 → 401")
    void checkIn_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkInBody(OFFICE_LAT, OFFICE_LNG))))
                .andExpect(status().isUnauthorized());
    }
}
