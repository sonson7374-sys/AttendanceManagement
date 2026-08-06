class ChangeRequestSubmit {
  const ChangeRequestSubmit({
    required this.recordId,
    required this.changeType,
    required this.reason,
    this.requestedCheckIn,
    this.requestedCheckOut,
    this.requestedWorkplaceId,
  });

  final int recordId;
  final String changeType;
  final String reason;
  final DateTime? requestedCheckIn;
  final DateTime? requestedCheckOut;
  final int? requestedWorkplaceId;

  Map<String, dynamic> toJson() => {
        'recordId': recordId,
        'changeType': changeType,
        'reason': reason,
        if (requestedCheckIn != null)
          'requestedCheckIn': requestedCheckIn!.toUtc().toIso8601String(),
        if (requestedCheckOut != null)
          'requestedCheckOut': requestedCheckOut!.toUtc().toIso8601String(),
        if (requestedWorkplaceId != null)
          'requestedWorkplaceId': requestedWorkplaceId,
      };
}

class ChangeRequest {
  const ChangeRequest({
    required this.id,
    required this.workDate,
    required this.requestType,
    required this.status,
    required this.reason,
    this.requestedCheckIn,
    this.requestedCheckOut,
    this.reviewerComment,
    required this.createdAt,
  });

  final int id;
  final String workDate;
  final String requestType;
  final String status;
  final String reason;
  final DateTime? requestedCheckIn;
  final DateTime? requestedCheckOut;
  final String? reviewerComment;
  final DateTime createdAt;

  factory ChangeRequest.fromJson(Map<String, dynamic> json) => ChangeRequest(
        id: json['id'] as int,
        // 백엔드(ChangeRequestResponse)의 실제 키는 targetDate/changeType
        workDate: json['targetDate'] as String,
        requestType: json['changeType'] as String,
        status: json['status'] as String,
        reason: json['reason'] as String? ?? '',
        requestedCheckIn: json['requestedCheckIn'] != null
            ? DateTime.parse(json['requestedCheckIn'] as String)
            : null,
        requestedCheckOut: json['requestedCheckOut'] != null
            ? DateTime.parse(json['requestedCheckOut'] as String)
            : null,
        // 승인/반려 코멘트는 승인 이력(ApprovalHistory)에 있어 이 응답에는 포함되지 않는다.
        reviewerComment: json['reviewerComment'] as String?,
        createdAt: DateTime.parse(json['createdAt'] as String),
      );
}

// 요청 유형 목록 (백엔드 ChangeRequestType enum과 정확히 일치해야 한다)
const changeRequestTypes = [
  ('CHECK_IN_TIME', '출근 시간 수정'),
  ('CHECK_OUT_TIME', '퇴근 시간 수정'),
  ('LATE_CORRECTION', '지각 사유'),
  ('ABSENT_CORRECTION', '결근 사유'),
  ('WORKPLACE_CHANGE', '근무지 변경'),
];

extension ChangeRequestStatusExt on String {
  String get displayName => switch (this) {
        'PENDING' => '검토 중',
        'APPROVED' => '승인',
        'REJECTED' => '반려',
        'CANCELED' => '취소됨',
        _ => this,
      };

  String get colorKey => switch (this) {
        'PENDING' => 'tertiary',
        'APPROVED' => 'primary',
        'REJECTED' => 'error',
        _ => 'outline',
      };
}
