class WorkplaceChangeRequest {
  const WorkplaceChangeRequest({
    required this.id,
    required this.requesterId,
    this.requesterName,
    this.currentWorkplaceId,
    this.currentWorkplaceName,
    required this.name,
    this.address,
    this.detailAddress,
    required this.type,
    required this.latitude,
    required this.longitude,
    required this.radiusMeters,
    this.maxAccuracyMeters,
    required this.checkInAllowed,
    required this.checkOutAllowed,
    required this.effectiveDate,
    required this.reason,
    required this.status,
    this.resultingWorkplaceId,
    required this.createdAt,
  });

  final int id;
  final int requesterId;
  final String? requesterName;
  final int? currentWorkplaceId;
  final String? currentWorkplaceName;
  final String name;
  final String? address;
  final String? detailAddress;
  final String type;
  final double latitude;
  final double longitude;
  final int radiusMeters;
  final int? maxAccuracyMeters;
  final bool checkInAllowed;
  final bool checkOutAllowed;
  final String effectiveDate;
  final String reason;
  final String status;
  final int? resultingWorkplaceId;
  final String createdAt;

  factory WorkplaceChangeRequest.fromJson(Map<String, dynamic> json) => WorkplaceChangeRequest(
        id: json['id'] as int,
        requesterId: json['requesterId'] as int,
        requesterName: json['requesterName'] as String?,
        currentWorkplaceId: json['currentWorkplaceId'] as int?,
        currentWorkplaceName: json['currentWorkplaceName'] as String?,
        name: json['name'] as String,
        address: json['address'] as String?,
        detailAddress: json['detailAddress'] as String?,
        type: json['type'] as String,
        latitude: (json['latitude'] as num).toDouble(),
        longitude: (json['longitude'] as num).toDouble(),
        radiusMeters: json['radiusMeters'] as int,
        maxAccuracyMeters: json['maxAccuracyMeters'] as int?,
        checkInAllowed: json['checkInAllowed'] as bool,
        checkOutAllowed: json['checkOutAllowed'] as bool,
        effectiveDate: json['effectiveDate'] as String,
        reason: json['reason'] as String,
        status: json['status'] as String,
        resultingWorkplaceId: json['resultingWorkplaceId'] as int?,
        createdAt: json['createdAt'] as String,
      );
}

class WorkplaceChangeRequestPayload {
  const WorkplaceChangeRequestPayload({
    this.currentWorkplaceId,
    required this.name,
    this.address,
    this.detailAddress,
    required this.type,
    required this.latitude,
    required this.longitude,
    required this.radiusMeters,
    this.maxAccuracyMeters,
    required this.checkInAllowed,
    required this.checkOutAllowed,
    required this.effectiveDate,
    required this.reason,
  });

  final int? currentWorkplaceId;
  final String name;
  final String? address;
  final String? detailAddress;
  final String type;
  final double latitude;
  final double longitude;
  final int radiusMeters;
  final int? maxAccuracyMeters;
  final bool checkInAllowed;
  final bool checkOutAllowed;
  final String effectiveDate;
  final String reason;

  Map<String, dynamic> toJson() => {
        if (currentWorkplaceId != null) 'currentWorkplaceId': currentWorkplaceId,
        'name': name,
        if (address != null && address!.isNotEmpty) 'address': address,
        if (detailAddress != null && detailAddress!.isNotEmpty) 'detailAddress': detailAddress,
        'type': type,
        'latitude': latitude,
        'longitude': longitude,
        'radiusMeters': radiusMeters,
        if (maxAccuracyMeters != null) 'maxAccuracyMeters': maxAccuracyMeters,
        'checkInAllowed': checkInAllowed,
        'checkOutAllowed': checkOutAllowed,
        'effectiveDate': effectiveDate,
        'reason': reason,
      };
}
