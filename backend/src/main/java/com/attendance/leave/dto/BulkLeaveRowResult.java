package com.attendance.leave.dto;

public record BulkLeaveRowResult(int rowNumber, String name, boolean success, String message) {
}
