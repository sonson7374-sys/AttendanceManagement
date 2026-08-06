// 휴가(연차/반차/반반차/병가/공가/연장근무/휴일근무) + 외근·출장·재택근무 신청을
// 하나의 "신청" 화면에서 다루기 위한 모델. 백엔드는 두 개의 별도 도메인
// (leave-requests / outside-work-requests)이지만, 사용자 입장에서는 스펙 6.7의
// "휴가·외근·출장·재택 신청" 하나의 메뉴로 묶여 있다.

/// 외근/출장/재택근무 여부 — 이 3종만 목적지 관련 추가 입력이 필요하다.
const outsideWorkRequestTypeValues = ['OUTSIDE_WORK', 'BUSINESS_TRIP', 'REMOTE_WORK'];

const leaveRequestTypes = [
  ('ANNUAL', '연차'),
  ('HALF_DAY', '반차'),
  ('HOURLY', '반반차'),
  ('SICK', '병가'),
  ('OFFICIAL', '공가'),
  ('OVERTIME', '연장근무'),
  ('HOLIDAY_WORK', '휴일근무'),
  ('ZERO_DAY', '대체휴가'),
  ('EARLY', '조기퇴근'),
  ('OUTSIDE_WORK', '외근'),
  ('BUSINESS_TRIP', '출장'),
  ('REMOTE_WORK', '재택근무'),
];

bool isOutsideWorkType(String requestType) =>
    outsideWorkRequestTypeValues.contains(requestType);

class LeaveRequestSubmit {
  const LeaveRequestSubmit({
    required this.requestType,
    required this.startAt,
    required this.endAt,
    required this.reason,
    this.destinationAddress,
    this.destinationLatitude,
    this.destinationLongitude,
    this.tempRadiusMeters,
    this.visitPurpose,
    this.clientName,
    this.expectedReturnAt,
  });

  final String requestType;
  final DateTime startAt;
  final DateTime endAt;
  final String reason;
  final String? destinationAddress;
  final double? destinationLatitude;
  final double? destinationLongitude;
  final int? tempRadiusMeters;
  final String? visitPurpose;
  final String? clientName;
  final DateTime? expectedReturnAt;

  bool get isOutsideWork => isOutsideWorkType(requestType);

  Map<String, dynamic> toJson() => {
        'requestType': requestType,
        'startAt': startAt.toUtc().toIso8601String(),
        'endAt': endAt.toUtc().toIso8601String(),
        'reason': reason,
        if (isOutsideWork && destinationAddress != null)
          'destinationAddress': destinationAddress,
        if (isOutsideWork && destinationLatitude != null)
          'destinationLatitude': destinationLatitude,
        if (isOutsideWork && destinationLongitude != null)
          'destinationLongitude': destinationLongitude,
        if (isOutsideWork && tempRadiusMeters != null)
          'tempRadiusMeters': tempRadiusMeters,
        if (isOutsideWork && visitPurpose != null) 'visitPurpose': visitPurpose,
        if (isOutsideWork && clientName != null) 'clientName': clientName,
        if (isOutsideWork && expectedReturnAt != null)
          'expectedReturnAt': expectedReturnAt!.toUtc().toIso8601String(),
      };
}

class LeaveRequestItem {
  const LeaveRequestItem({
    required this.id,
    required this.requestType,
    required this.startAt,
    required this.endAt,
    required this.reason,
    required this.status,
    required this.createdAt,
    this.destinationAddress,
    this.clientName,
  });

  final int id;
  final String requestType;
  final DateTime startAt;
  final DateTime endAt;
  final String reason;
  final String status;
  final DateTime createdAt;
  final String? destinationAddress;
  final String? clientName;

  bool get isOutsideWork => isOutsideWorkType(requestType);

  factory LeaveRequestItem.fromJson(Map<String, dynamic> json) => LeaveRequestItem(
        id: json['id'] as int,
        requestType: json['requestType'] as String,
        startAt: DateTime.parse(json['startAt'] as String),
        endAt: DateTime.parse(json['endAt'] as String),
        reason: json['reason'] as String? ?? '',
        status: json['status'] as String,
        createdAt: DateTime.parse(json['createdAt'] as String),
        destinationAddress: json['destinationAddress'] as String?,
        clientName: json['clientName'] as String?,
      );
}

String leaveRequestTypeLabel(String requestType) => leaveRequestTypes
    .firstWhere((t) => t.$1 == requestType, orElse: () => (requestType, requestType))
    .$2;
