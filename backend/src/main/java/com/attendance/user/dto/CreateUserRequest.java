package com.attendance.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {

    @NotBlank(message = "이메일을 입력해주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String password;

    @NotBlank(message = "이름을 입력해주세요.")
    private String name;

    private String employeeNumber;
    private String phone;

    // 회사 소속은 클라이언트 값을 신뢰하지 않고 서버가 로그인한 관리자의 소속 회사로 강제한다(UserService.createUser 참고).
    private Long companyId;

    private Long organizationId;
    private String jobTitle;
    private String employmentType;
    private LocalDate hireDate;
    private Long defaultWorkplaceId;
    private String role;

    @Size(max = 20, message = "권한레벨은 20자를 초과할 수 없습니다.")
    private String level;
}
