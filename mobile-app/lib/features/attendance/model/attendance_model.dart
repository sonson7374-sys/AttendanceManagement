class CheckInRequest {
  const CheckInRequest({
    required this.workplaceId,
    required this.latitude,
    required this.longitude,
    required this.accuracyMeters,
    required this.capturedAt,
    required this.deviceId,
    required this.devicePlatform,
    required this.mockLocationDetected,
  });

  final int workplaceId;
  final double latitude;
  final double longitude;
  final double accuracyMeters;
  final DateTime capturedAt;
  final String deviceId;
  final String devicePlatform;
  final bool mockLocationDetected;

  Map<String, dynamic> toJson() => {
        'workplaceId': workplaceId,
        'latitude': latitude,
        'longitude': longitude,
        'accuracyMeters': accuracyMeters,
        'capturedAt': capturedAt.toUtc().toIso8601String(),
        'deviceId': deviceId,
        'devicePlatform': devicePlatform,
        'mockLocationDetected': mockLocationDetected,
      };
}

class CheckOutRequest {
  const CheckOutRequest({
    required this.latitude,
    required this.longitude,
    required this.accuracyMeters,
    required this.capturedAt,
    required this.deviceId,
    required this.devicePlatform,
    required this.mockLocationDetected,
  });

  final double latitude;
  final double longitude;
  final double accuracyMeters;
  final DateTime capturedAt;
  final String deviceId;
  final String devicePlatform;
  final bool mockLocationDetected;

  Map<String, dynamic> toJson() => {
        'latitude': latitude,
        'longitude': longitude,
        'accuracyMeters': accuracyMeters,
        'capturedAt': capturedAt.toUtc().toIso8601String(),
        'deviceId': deviceId,
        'devicePlatform': devicePlatform,
        'mockLocationDetected': mockLocationDetected,
      };
}

class TodayAttendance {
  const TodayAttendance({
    required this.recordId,
    required this.status,
    required this.workDate,
    this.checkInAt,
    this.checkOutAt,
    this.workplaceName,
    this.workMinutes,
    this.isLate,
    this.isEarlyLeave,
  });

  final int recordId;
  final String status;
  final String workDate;
  final DateTime? checkInAt;
  final DateTime? checkOutAt;
  final String? workplaceName;
  final int? workMinutes;
  final bool? isLate;
  final bool? isEarlyLeave;

  bool get canCheckIn =>
      status == 'BEFORE_WORK' || status == 'ABSENT';

  bool get canCheckOut =>
      status == 'WORKING' || status == 'LATE' || status == 'BREAK';

  bool get canStartBreak => status == 'WORKING' || status == 'LATE';

  bool get canEndBreak => status == 'BREAK';

  factory TodayAttendance.fromJson(Map<String, dynamic> json) =>
      TodayAttendance(
        // 백엔드(AttendanceResponse/TodayAttendanceResponse)의 실제 키는 attendanceId
        recordId: json['attendanceId'] as int? ?? 0,
        status: json['status'] as String,
        workDate: json['workDate'] as String,
        checkInAt: json['checkInAt'] != null
            ? DateTime.parse(json['checkInAt'] as String)
            : null,
        checkOutAt: json['checkOutAt'] != null
            ? DateTime.parse(json['checkOutAt'] as String)
            : null,
        workplaceName: json['workplaceName'] as String?,
        workMinutes: json['workMinutes'] as int?,
        isLate: json['late'] as bool?,
        isEarlyLeave: json['earlyLeave'] as bool?,
      );
}

class AttendanceRecord {
  const AttendanceRecord({
    required this.recordId,
    required this.workDate,
    required this.status,
    this.checkInAt,
    this.checkOutAt,
    this.workplaceName,
    this.workMinutes,
    this.breakMinutes,
    this.overtimeMinutes,
    this.nightMinutes,
    this.isLate,
    this.isEarlyLeave,
  });

  final int recordId;
  final String workDate;
  final String status;
  final DateTime? checkInAt;
  final DateTime? checkOutAt;
  final String? workplaceName;
  final int? workMinutes;
  final int? breakMinutes;
  final int? overtimeMinutes;
  final int? nightMinutes;
  final bool? isLate;
  final bool? isEarlyLeave;

  factory AttendanceRecord.fromJson(Map<String, dynamic> json) =>
      AttendanceRecord(
        // 백엔드(AttendanceHistoryResponse)의 실제 키는 attendanceId
        recordId: json['attendanceId'] as int,
        workDate: json['workDate'] as String,
        status: json['status'] as String,
        checkInAt: json['checkInAt'] != null
            ? DateTime.parse(json['checkInAt'] as String)
            : null,
        checkOutAt: json['checkOutAt'] != null
            ? DateTime.parse(json['checkOutAt'] as String)
            : null,
        workplaceName: json['workplaceName'] as String?,
        workMinutes: json['workMinutes'] as int?,
        breakMinutes: json['breakMinutes'] as int?,
        overtimeMinutes: json['overtimeMinutes'] as int?,
        nightMinutes: json['nightMinutes'] as int?,
        isLate: json['late'] as bool?,
        isEarlyLeave: json['earlyLeave'] as bool?,
      );
}

// 근태 상태 한글 변환
extension AttendanceStatusExt on String {
  String get displayName => switch (this) {
        'BEFORE_WORK' => '미출근',
        'WORKING' => '근무 중',
        'BREAK' => '휴게',
        'FINISHED' => '퇴근',
        'LATE' => '지각',
        'EARLY_LEAVE' => '조퇴',
        'ABSENT' => '결근',
        'LEAVE' => '휴가',
        'OUTSIDE_WORK' => '외근',
        'BUSINESS_TRIP' => '출장',
        'REMOTE_WORK' => '재택',
        _ => this,
      };

  // 상태 색상 (Material ColorScheme 활용을 위해 문자열로 구분)
  String get colorKey => switch (this) {
        'WORKING' => 'primary',
        'BREAK' => 'tertiary',
        'LATE' => 'error',
        'EARLY_LEAVE' => 'error',
        'ABSENT' => 'error',
        'FINISHED' => 'secondary',
        _ => 'outline',
      };
}
