package com.attendance.user.dto;

public record BulkUserRowResult(int rowNumber, String email, boolean success, String message) {
}
