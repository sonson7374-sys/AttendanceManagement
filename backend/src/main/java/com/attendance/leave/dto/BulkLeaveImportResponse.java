package com.attendance.leave.dto;

import java.util.List;

public record BulkLeaveImportResponse(
        int totalRows,
        int successCount,
        int failureCount,
        List<BulkLeaveRowResult> results
) {
}
