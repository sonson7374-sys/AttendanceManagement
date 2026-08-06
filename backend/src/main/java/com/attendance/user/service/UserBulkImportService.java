package com.attendance.user.service;

import com.attendance.audit.service.AuditLogService;
import com.attendance.commoncode.domain.CommonCode;
import com.attendance.commoncode.repository.CommonCodeRepository;
import com.attendance.common.exception.AttendanceException;
import com.attendance.organization.domain.Organization;
import com.attendance.organization.repository.OrganizationRepository;
import com.attendance.user.dto.BulkUserImportResponse;
import com.attendance.user.dto.BulkUserRowResult;
import com.attendance.user.dto.CreateUserRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 엑셀 파일을 통한 직원 일괄 등록. 행 단위로 독립 처리하여 일부 행이 실패해도
 * 나머지 행 등록에는 영향을 주지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBulkImportService {

    private static final int HEADER_ROW_INDEX = 0;
    private static final String LEVEL_GROUP_CODE = "LEVEL_ROLL";
    private static final String DEFAULT_LEVEL_CODE = "EMPLOYEE";
    private static final String[] TEMPLATE_HEADERS = {
            "이메일*", "비밀번호*", "이름*", "사번", "휴대전화",
            "직급", "고용형태", "입사일(yyyy-MM-dd)", "역할(EMPLOYEE/MANAGER/HR_ADMIN/SYSTEM_ADMIN)",
            "소속부서",
    };

    private final UserService userService;
    private final AuditLogService auditLogService;
    private final Validator validator;
    private final OrganizationRepository organizationRepository;
    private final CommonCodeRepository commonCodeRepository;

    public BulkUserImportResponse importFromExcel(Long actorId, String actorEmail, Long companyId, MultipartFile file) {
        List<BulkUserRowResult> results = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        // 직급(예: "팀장")이 LEVEL_ROLL 권한명과 동일하면 그 권한레벨을 부여하고, 일치하는 권한명이
        // 없으면(예: "부장(수석)") 기본 권한레벨인 직원(EMPLOYEE)으로 등록한다.
        Map<String, String> levelCodeByName = commonCodeRepository.findByGroupCodeOrderByDisplayOrderAsc(LEVEL_GROUP_CODE)
                .stream()
                .filter(CommonCode::isActive)
                .collect(Collectors.toMap(CommonCode::getCodeName, CommonCode::getCode, (a, b) -> a));

        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            for (int rowIdx = HEADER_ROW_INDEX + 1; rowIdx <= lastRow; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null || isRowBlank(row, formatter)) continue;

                int displayRow = rowIdx + 1; // 엑셀 상 실제 행 번호(1-based)
                String email = cellString(row, 0, formatter);

                try {
                    String departmentName = blankToNull(cellString(row, 9, formatter));
                    Long organizationId = null;
                    if (departmentName != null) {
                        Organization org = organizationRepository.findByCompanyIdAndName(companyId, departmentName)
                                .orElse(null);
                        if (org == null) {
                            results.add(new BulkUserRowResult(displayRow, email, false,
                                    "소속부서 '" + departmentName + "'을 찾을 수 없습니다."));
                            continue;
                        }
                        organizationId = org.getId();
                    }

                    String jobTitle = blankToNull(cellString(row, 5, formatter));
                    String levelCode = jobTitle != null ? levelCodeByName.getOrDefault(jobTitle, DEFAULT_LEVEL_CODE) : DEFAULT_LEVEL_CODE;

                    CreateUserRequest request = CreateUserRequest.builder()
                            .email(email)
                            .password(cellString(row, 1, formatter))
                            .name(cellString(row, 2, formatter))
                            .employeeNumber(blankToNull(cellString(row, 3, formatter)))
                            .phone(blankToNull(cellString(row, 4, formatter)))
                            .companyId(companyId)
                            .organizationId(organizationId)
                            .jobTitle(jobTitle)
                            .employmentType(blankToNull(cellString(row, 6, formatter)))
                            .hireDate(parseDate(row.getCell(7), cellString(row, 7, formatter)))
                            .role(blankToNull(cellString(row, 8, formatter)))
                            .level(levelCode)
                            .build();

                    Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
                    if (!violations.isEmpty()) {
                        String message = violations.stream()
                                .map(ConstraintViolation::getMessage)
                                .distinct()
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("입력값이 올바르지 않습니다.");
                        results.add(new BulkUserRowResult(displayRow, email, false, message));
                        continue;
                    }

                    userService.createUser(request);
                    results.add(new BulkUserRowResult(displayRow, email, true, "등록 완료"));
                } catch (AttendanceException e) {
                    results.add(new BulkUserRowResult(displayRow, email, false, e.getMessage()));
                } catch (Exception e) {
                    log.warn("직원 일괄 등록 중 행 처리 실패: row={}", displayRow, e);
                    results.add(new BulkUserRowResult(displayRow, email, false, "처리 중 오류가 발생했습니다."));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("엑셀 파일을 읽는 중 오류가 발생했습니다.", e);
        }

        long successCount = results.stream().filter(BulkUserRowResult::success).count();
        int failureCount = results.size() - (int) successCount;

        auditLogService.record(actorId, actorEmail, "USER_BULK_IMPORT", "USER", null,
                Map.of("totalRows", results.size(), "successCount", successCount, "failureCount", failureCount));

        return new BulkUserImportResponse(results.size(), (int) successCount, failureCount, results);
    }

    /** 관리자가 형식을 참고할 수 있는 등록용 엑셀 템플릿을 생성한다. */
    public byte[] generateTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("직원 일괄등록");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            Row example = sheet.createRow(1);
            String[] sample = {
                    "hong@company.com", "Passw0rd!", "홍길동", "EMP-1001", "010-1234-5678",
                    "사원", "정규직", "2026-01-02", "EMPLOYEE", "미디어운영팀",
            };
            for (int i = 0; i < sample.length; i++) {
                example.createCell(i).setCellValue(sample[i]);
            }

            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("템플릿 생성 중 오류가 발생했습니다.", e);
        }
    }

    private boolean isRowBlank(Row row, DataFormatter formatter) {
        for (int c = 0; c < 3; c++) { // 이메일/비밀번호/이름 중 하나라도 있으면 유효 행으로 간주
            if (!cellString(row, c, formatter).isBlank()) return false;
        }
        return true;
    }

    private String cellString(Row row, int index, DataFormatter formatter) {
        Cell cell = row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private LocalDate parseDate(Cell cell, String text) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        if (text == null || text.isBlank()) return null;
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
