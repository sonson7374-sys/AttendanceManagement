package com.attendance.leave.service;

import com.attendance.audit.service.AuditLogService;
import com.attendance.common.config.AppConfig;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.leave.domain.LeaveRequest;
import com.attendance.leave.domain.LeaveRequestType;
import com.attendance.leave.dto.BulkLeaveImportResponse;
import com.attendance.leave.dto.BulkLeaveRowResult;
import com.attendance.leave.repository.LeaveRequestRepository;
import com.attendance.user.domain.User;
import com.attendance.user.repository.UserRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 엑셀 파일을 통한 휴가 신청 일괄 등록(관리자 전용). 이미 처리(승인 완료)된 과거 이력을
 * 옮겨 담는 용도이므로, 행별로 독립 처리해서 일부 실패해도 나머지 등록에는 영향을 주지 않고
 * 등록된 건은 곧바로 APPROVED 상태로 저장한다(별도 승인 절차 없음, 근태 기록 자동 반영은 하지 않음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveRequestBulkImportService {

    private static final int HEADER_ROW_INDEX = 0;

    private static final String COL_NAME = "이름";
    private static final String COL_TYPE = "구분";
    private static final String COL_START_DATE = "시작일";
    private static final String COL_START_TIME = "시작시간";
    private static final String COL_END_DATE = "종료일";
    private static final String COL_END_TIME = "종료시간";
    private static final String COL_REASON = "사유";
    private static final String COL_DELETED = "삭제여부";

    private static final List<String> REQUIRED_HEADERS =
            List.of(COL_NAME, COL_TYPE, COL_START_DATE, COL_END_DATE, COL_REASON);

    private static final Map<String, LeaveRequestType> TYPE_LABELS = new LinkedHashMap<>();
    static {
        TYPE_LABELS.put("연차", LeaveRequestType.ANNUAL);
        TYPE_LABELS.put("반차", LeaveRequestType.HALF_DAY);
        TYPE_LABELS.put("오전반차", LeaveRequestType.HALF_DAY);
        TYPE_LABELS.put("오후반차", LeaveRequestType.HALF_DAY);
        TYPE_LABELS.put("오전/오후반차", LeaveRequestType.HALF_DAY);
        TYPE_LABELS.put("반반차", LeaveRequestType.HOURLY);
        TYPE_LABELS.put("병가", LeaveRequestType.SICK);
        TYPE_LABELS.put("공가", LeaveRequestType.OFFICIAL);
        TYPE_LABELS.put("연장근무", LeaveRequestType.OVERTIME);
        TYPE_LABELS.put("휴일근무", LeaveRequestType.HOLIDAY_WORK);
        TYPE_LABELS.put("대체휴가", LeaveRequestType.ZERO_DAY);
        TYPE_LABELS.put("조기퇴근", LeaveRequestType.EARLY);
    }

    private static final List<String> DELETED_TRUE_VALUES =
            List.of("Y", "YES", "예", "O", "TRUE", "1", "삭제");

    // 등록에 실제로 쓰이는 건 이름/구분/시작일/시작시간/종료일/종료시간/사유뿐이지만,
    // 관리자가 이미 쓰던 인사 자료(부서·직급·유형·신청일수·연차차감·삭제여부 포함) 형식 그대로
    // 내려받아 바로 업로드할 수 있도록 전체 컬럼을 템플릿에 포함한다.
    private static final String[] TEMPLATE_HEADERS = {
            "이름", "부서", "직급", "유형", "구분", "시작일", "시작시간", "종료일", "종료시간",
            "신청일수", "연차차감", "사유", "삭제여부",
    };

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public BulkLeaveImportResponse importFromExcel(Long actorId, String actorEmail, MultipartFile file) {
        List<BulkLeaveRowResult> results = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> columns = readHeaderColumns(sheet.getRow(HEADER_ROW_INDEX), formatter);
            int lastRow = sheet.getLastRowNum();

            for (int rowIdx = HEADER_ROW_INDEX + 1; rowIdx <= lastRow; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null || cellString(row, columns.get(COL_NAME), formatter).isBlank()) continue;

                int displayRow = rowIdx + 1; // 엑셀 상 실제 행 번호(1-based)
                String name = cellString(row, columns.get(COL_NAME), formatter);

                // 삭제여부가 표시된 행은 등록 대상이 아니므로 결과에도 남기지 않고 건너뛴다.
                if (isDeleted(row, columns.get(COL_DELETED), formatter)) continue;

                try {
                    results.add(importRow(row, displayRow, name, columns, formatter, actorId));
                } catch (AttendanceException e) {
                    results.add(new BulkLeaveRowResult(displayRow, name, false, e.getMessage()));
                } catch (Exception e) {
                    log.warn("휴가 일괄 등록 중 행 처리 실패: row={}", displayRow, e);
                    results.add(new BulkLeaveRowResult(displayRow, name, false, "처리 중 오류가 발생했습니다."));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("엑셀 파일을 읽는 중 오류가 발생했습니다.", e);
        }

        long successCount = results.stream().filter(BulkLeaveRowResult::success).count();
        int failureCount = results.size() - (int) successCount;

        auditLogService.record(actorId, actorEmail, "LEAVE_REQUEST_BULK_IMPORT", "LEAVE_REQUEST", null,
                Map.of("totalRows", results.size(), "successCount", successCount, "failureCount", failureCount));

        return new BulkLeaveImportResponse(results.size(), (int) successCount, failureCount, results);
    }

    /** 관리자가 형식을 참고할 수 있는 일괄등록용 엑셀 템플릿을 생성한다. */
    public byte[] generateTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("휴가 일괄등록");

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
            Object[] sample = {
                    "홍길동", "미디어운영팀", "대리", "법정휴가", "연차",
                    "2026-08-03", "09:00", "2026-08-03", "18:00", 1, 1, "개인 사유", "",
            };
            for (int i = 0; i < sample.length; i++) {
                Cell cell = example.createCell(i);
                if (sample[i] instanceof Number n) {
                    cell.setCellValue(n.doubleValue());
                } else {
                    cell.setCellValue(String.valueOf(sample[i]));
                }
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

    private BulkLeaveRowResult importRow(Row row, int displayRow, String name, Map<String, Integer> columns,
                                          DataFormatter formatter, Long actorId) {
        List<User> matches = userRepository.findByName(name.trim());
        if (matches.isEmpty()) {
            return new BulkLeaveRowResult(displayRow, name, false, "해당 이름의 사용자를 찾을 수 없습니다.");
        }
        if (matches.size() > 1) {
            return new BulkLeaveRowResult(displayRow, name, false,
                    "동명이인이 " + matches.size() + "명 있어 사용자를 특정할 수 없습니다.");
        }
        User user = matches.get(0);

        String typeText = cellString(row, columns.get(COL_TYPE), formatter).trim();
        LeaveRequestType type = TYPE_LABELS.get(typeText);
        if (type == null) {
            return new BulkLeaveRowResult(displayRow, name, false, "알 수 없는 구분 값입니다: " + typeText);
        }

        LocalDate startDate = parseDate(row, columns.get(COL_START_DATE), formatter);
        LocalDate endDate = parseDate(row, columns.get(COL_END_DATE), formatter);
        if (startDate == null || endDate == null) {
            return new BulkLeaveRowResult(displayRow, name, false, "시작일/종료일 형식이 올바르지 않습니다.");
        }

        LocalTime startTime = columns.containsKey(COL_START_TIME)
                ? parseTime(row, columns.get(COL_START_TIME), formatter, LocalTime.MIN)
                : LocalTime.MIN;
        LocalTime endTime = columns.containsKey(COL_END_TIME)
                ? parseTime(row, columns.get(COL_END_TIME), formatter, LocalTime.of(23, 59, 59))
                : LocalTime.of(23, 59, 59);

        Instant startAt = LocalDateTime.of(startDate, startTime).atZone(AppConfig.SEOUL).toInstant();
        Instant endAt = LocalDateTime.of(endDate, endTime).atZone(AppConfig.SEOUL).toInstant();
        if (endAt.isBefore(startAt)) {
            return new BulkLeaveRowResult(displayRow, name, false, "종료일시가 시작일시보다 빠릅니다.");
        }

        String reason = columns.containsKey(COL_REASON) ? cellString(row, columns.get(COL_REASON), formatter) : "";

        LeaveRequest request = LeaveRequest.create(user.getId(), user.getEmployeeNumber(), type, startAt, endAt, reason);
        request.approve(actorId);
        leaveRequestRepository.save(request);

        return new BulkLeaveRowResult(displayRow, name, true, "등록 완료(승인)");
    }

    private Map<String, Integer> readHeaderColumns(Row headerRow, DataFormatter formatter) {
        if (headerRow == null) {
            throw new AttendanceException(ErrorCode.INVALID_INPUT, "헤더 행을 찾을 수 없습니다.");
        }
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String text = formatter.formatCellValue(cell).trim();
            if (!text.isBlank()) columns.put(text, cell.getColumnIndex());
        }
        List<String> missing = REQUIRED_HEADERS.stream().filter(h -> !columns.containsKey(h)).toList();
        if (!missing.isEmpty()) {
            throw new AttendanceException(ErrorCode.INVALID_INPUT,
                    "필수 헤더가 없습니다: " + String.join(", ", missing));
        }
        return columns;
    }

    private boolean isDeleted(Row row, Integer colIndex, DataFormatter formatter) {
        if (colIndex == null) return false;
        String value = cellString(row, colIndex, formatter).trim();
        return DELETED_TRUE_VALUES.stream().anyMatch(v -> v.equalsIgnoreCase(value));
    }

    private String cellString(Row row, Integer index, DataFormatter formatter) {
        if (index == null) return "";
        Cell cell = row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private LocalDate parseDate(Row row, Integer index, DataFormatter formatter) {
        if (index == null) return null;
        Cell cell = row.getCell(index);
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String text = cellString(row, index, formatter);
        if (text.isBlank()) return null;
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalTime parseTime(Row row, Integer index, DataFormatter formatter, LocalTime fallback) {
        Cell cell = row.getCell(index);
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalTime();
        }
        String text = cellString(row, index, formatter);
        if (text.isBlank()) return fallback;
        try {
            return LocalTime.parse(text.length() == 5 ? text : text.substring(0, 5));
        } catch (Exception e) {
            return fallback;
        }
    }
}
