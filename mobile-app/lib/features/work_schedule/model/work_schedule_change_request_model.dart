/// 관리자웹 WorkScheduleChangeRequest와 동일한 필드 구성.
/// 근무지 변경요청과 달리, 근무제 변경요청은 새 값을 직접 입력하지 않고
/// 기존에 등록된 활성 근무제 중 하나(targetWorkScheduleId)를 선택하는 방식이다.
class WorkScheduleChangeRequest {
  WorkScheduleChangeRequest({
    required this.id,
    required this.requesterId,
    this.requesterName,
    this.currentWorkScheduleId,
    this.currentWorkScheduleName,
    required this.targetWorkScheduleId,
    this.targetWorkScheduleName,
    required this.effectiveMonth,
    required this.reason,
    required this.status,
    required this.createdAt,
  });

  final int id;
  final int requesterId;
  final String? requesterName;
  final int? currentWorkScheduleId;
  final String? currentWorkScheduleName;
  final int targetWorkScheduleId;
  final String? targetWorkScheduleName;
  final String effectiveMonth;
  final String reason;
  final String status;
  final String createdAt;

  factory WorkScheduleChangeRequest.fromJson(Map<String, dynamic> json) {
    return WorkScheduleChangeRequest(
      id: json['id'] as int,
      requesterId: json['requesterId'] as int,
      requesterName: json['requesterName'] as String?,
      currentWorkScheduleId: json['currentWorkScheduleId'] as int?,
      currentWorkScheduleName: json['currentWorkScheduleName'] as String?,
      targetWorkScheduleId: json['targetWorkScheduleId'] as int,
      targetWorkScheduleName: json['targetWorkScheduleName'] as String?,
      effectiveMonth: json['effectiveMonth'] as String,
      reason: json['reason'] as String,
      status: json['status'] as String,
      createdAt: json['createdAt'] as String,
    );
  }
}

class WorkScheduleChangeRequestPayload {
  WorkScheduleChangeRequestPayload({
    this.currentWorkScheduleId,
    required this.targetWorkScheduleId,
    required this.effectiveMonth,
    required this.reason,
  });

  final int? currentWorkScheduleId;
  final int targetWorkScheduleId;
  final String effectiveMonth;
  final String reason;

  Map<String, dynamic> toJson() => {
        if (currentWorkScheduleId != null) 'currentWorkScheduleId': currentWorkScheduleId,
        'targetWorkScheduleId': targetWorkScheduleId,
        'effectiveMonth': effectiveMonth,
        'reason': reason,
      };
}
