package com.attendance.integration;

import com.attendance.auth.dto.LoginRequest;
import com.attendance.user.domain.User;
import com.attendance.user.domain.UserRole;
import com.attendance.user.repository.UserRepository;
import com.attendance.workplace.domain.Workplace;
import com.attendance.workplace.domain.UserWorkplace;
import com.attendance.workplace.repository.UserWorkplaceRepository;
import com.attendance.workplace.repository.WorkplaceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ChangeRequestIntegrationTest extends IntegrationTestBase {

    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired WorkplaceRepository workplaceRepository;
    @Autowired UserWorkplaceRepository userWorkplaceRepository;
    @Autowired PasswordEncoder passwordEncoder;

    User employee;
    User manager;
    String empToken;
    String mgrToken;

    @BeforeEach
    void setUp() throws Exception {
        employee = userRepository.save(User.builder()
                .email("emp@test.com").password(passwordEncoder.encode("Password1!"))
                .name("직원").employeeNumber("EMP-001").companyId(1L).role(UserRole.EMPLOYEE).build());

        manager = userRepository.save(User.builder()
                .email("mgr@test.com").password(passwordEncoder.encode("Password1!"))
                .name("매니저").employeeNumber("MGR-001").companyId(1L).role(UserRole.MANAGER).build());

        Workplace wp = workplaceRepository.save(Workplace.builder()
                .companyId(1L).name("사무소").address("서울")
                .latitude(new BigDecimal("37.5665")).longitude(new BigDecimal("126.9780"))
                .radiusMeters(500).build());

        userWorkplaceRepository.save(UserWorkplace.builder()
                .userId(employee.getId()).workplaceId(wp.getId()).build());

        empToken = obtainToken("emp@test.com", "Password1!");
        mgrToken = obtainToken("mgr@test.com", "Password1!");
    }

    private String obtainToken(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        ReflectionTestUtils.setField(req, "email", email);
        ReflectionTestUtils.setField(req, "password", password);
        var res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    @Test
    @DisplayName("수정 요청 제출 → 조회 시 PENDING 상태")
    void submitChangeRequest_andList() throws Exception {
        Map<String, Object> body = Map.of(
                "changeType", "ABSENT_CORRECTION",
                "targetDate", OffsetDateTime.now(ZoneOffset.UTC).toLocalDate().toString(),
                "reason", "외부 미팅으로 인한 재택"
        );

        mockMvc.perform(post("/api/v1/attendance/change-requests")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(get("/api/v1/attendance/change-requests/my")
                        .header("Authorization", "Bearer " + empToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("매니저가 수정 요청 승인 → 상태가 APPROVED로 변경")
    void approveChangeRequest() throws Exception {
        Map<String, Object> submitBody = Map.of(
                "changeType", "ABSENT_CORRECTION",
                "targetDate", OffsetDateTime.now(ZoneOffset.UTC).toLocalDate().toString(),
                "reason", "재택 처리 요청"
        );

        var submitRes = mockMvc.perform(post("/api/v1/attendance/change-requests")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitBody)))
                .andExpect(status().isOk()).andReturn();

        long requestId = objectMapper.readTree(submitRes.getResponse().getContentAsString())
                .at("/data/id").asLong();

        mockMvc.perform(patch("/api/v1/attendance/change-requests/" + requestId)
                        .header("Authorization", "Bearer " + mgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("action", "APPROVE", "comment", "확인 후 승인"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }
}
