/// 관리자웹 WORK_SCHEDULE_TYPE_OPTIONS와 동일한 근무제 유형.
enum WorkScheduleType { fixed, flextime, selective, elastic, shift, remote }

extension WorkScheduleTypeX on WorkScheduleType {
  static WorkScheduleType fromApi(String value) {
    switch (value) {
      case 'FIXED':
        return WorkScheduleType.fixed;
      case 'FLEXTIME':
        return WorkScheduleType.flextime;
      case 'SELECTIVE':
        return WorkScheduleType.selective;
      case 'ELASTIC':
        return WorkScheduleType.elastic;
      case 'SHIFT':
        return WorkScheduleType.shift;
      case 'REMOTE':
        return WorkScheduleType.remote;
      default:
        return WorkScheduleType.fixed;
    }
  }

  String toApi() {
    switch (this) {
      case WorkScheduleType.fixed:
        return 'FIXED';
      case WorkScheduleType.flextime:
        return 'FLEXTIME';
      case WorkScheduleType.selective:
        return 'SELECTIVE';
      case WorkScheduleType.elastic:
        return 'ELASTIC';
      case WorkScheduleType.shift:
        return 'SHIFT';
      case WorkScheduleType.remote:
        return 'REMOTE';
    }
  }

  String get label {
    switch (this) {
      case WorkScheduleType.fixed:
        return '고정 근무제';
      case WorkScheduleType.flextime:
        return '시차 출퇴근제';
      case WorkScheduleType.selective:
        return '선택 근무제';
      case WorkScheduleType.elastic:
        return '탄력 근무제';
      case WorkScheduleType.shift:
        return '교대 근무제';
      case WorkScheduleType.remote:
        return '재택 근무제';
    }
  }
}

const List<WorkScheduleType> kWorkScheduleTypeOptions = WorkScheduleType.values;

/// 관리자웹 WorkSchedule 타입과 동일한 필드 구성.
class WorkSchedule {
  WorkSchedule({
    required this.id,
    required this.companyId,
    required this.name,
    required this.workStartTime,
    required this.workEndTime,
    required this.requiredWorkMinutes,
    required this.overtimeThresholdMin,
    required this.defaultSchedule,
    required this.active,
    required this.scheduleType,
    required this.lateThresholdMinutes,
    required this.earlyLeaveThresholdMinutes,
    required this.breakMinutes,
    this.nightShiftStart,
    this.nightShiftEnd,
    required this.holidayWorkThresholdMinutes,
  });

  final int id;
  final int companyId;
  final String name;
  final String workStartTime;
  final String workEndTime;
  final int requiredWorkMinutes;
  final int overtimeThresholdMin;
  final bool defaultSchedule;
  final bool active;
  final WorkScheduleType scheduleType;
  final int lateThresholdMinutes;
  final int earlyLeaveThresholdMinutes;
  final int breakMinutes;
  final String? nightShiftStart;
  final String? nightShiftEnd;
  final int holidayWorkThresholdMinutes;

  factory WorkSchedule.fromJson(Map<String, dynamic> json) {
    return WorkSchedule(
      id: json['id'] as int,
      companyId: json['companyId'] as int,
      name: json['name'] as String,
      workStartTime: (json['workStartTime'] as String).substring(0, 5),
      workEndTime: (json['workEndTime'] as String).substring(0, 5),
      requiredWorkMinutes: json['requiredWorkMinutes'] as int,
      overtimeThresholdMin: json['overtimeThresholdMin'] as int,
      defaultSchedule: json['defaultSchedule'] as bool,
      active: json['active'] as bool,
      scheduleType: WorkScheduleTypeX.fromApi(json['scheduleType'] as String),
      lateThresholdMinutes: json['lateThresholdMinutes'] as int,
      earlyLeaveThresholdMinutes: json['earlyLeaveThresholdMinutes'] as int,
      breakMinutes: json['breakMinutes'] as int,
      nightShiftStart: (json['nightShiftStart'] as String?)?.substring(0, 5),
      nightShiftEnd: (json['nightShiftEnd'] as String?)?.substring(0, 5),
      holidayWorkThresholdMinutes: json['holidayWorkThresholdMinutes'] as int,
    );
  }
}

/// 등록·수정 요청 payload (백엔드 WorkScheduleRequest와 동일한 필드).
class WorkSchedulePayload {
  WorkSchedulePayload({
    required this.name,
    required this.workStartTime,
    required this.workEndTime,
    required this.requiredWorkMinutes,
    required this.overtimeThresholdMin,
    required this.defaultSchedule,
    required this.scheduleType,
    required this.lateThresholdMinutes,
    required this.earlyLeaveThresholdMinutes,
    required this.breakMinutes,
    this.nightShiftStart,
    this.nightShiftEnd,
    required this.holidayWorkThresholdMinutes,
  });

  final String name;
  final String workStartTime;
  final String workEndTime;
  final int requiredWorkMinutes;
  final int overtimeThresholdMin;
  final bool defaultSchedule;
  final WorkScheduleType scheduleType;
  final int lateThresholdMinutes;
  final int earlyLeaveThresholdMinutes;
  final int breakMinutes;
  final String? nightShiftStart;
  final String? nightShiftEnd;
  final int holidayWorkThresholdMinutes;

  Map<String, dynamic> toJson() => {
        'name': name,
        'workStartTime': workStartTime,
        'workEndTime': workEndTime,
        'requiredWorkMinutes': requiredWorkMinutes,
        'overtimeThresholdMin': overtimeThresholdMin,
        'defaultSchedule': defaultSchedule,
        'scheduleType': scheduleType.toApi(),
        'lateThresholdMinutes': lateThresholdMinutes,
        'earlyLeaveThresholdMinutes': earlyLeaveThresholdMinutes,
        'breakMinutes': breakMinutes,
        if (nightShiftStart != null) 'nightShiftStart': nightShiftStart,
        if (nightShiftEnd != null) 'nightShiftEnd': nightShiftEnd,
        'holidayWorkThresholdMinutes': holidayWorkThresholdMinutes,
      };
}
