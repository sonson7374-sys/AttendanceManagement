package com.attendance.user.dto;

import java.util.List;

public record BulkUserImportResponse(
        int totalRows,
        int successCount,
        int failureCount,
        List<BulkUserRowResult> results
) {
}
