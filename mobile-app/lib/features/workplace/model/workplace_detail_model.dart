enum WorkplaceType { office, largeSite, constructionSite, indoor, other }

extension WorkplaceTypeX on WorkplaceType {
  static WorkplaceType fromApi(String value) => switch (value) {
        'OFFICE' => WorkplaceType.office,
        'LARGE_SITE' => WorkplaceType.largeSite,
        'CONSTRUCTION_SITE' => WorkplaceType.constructionSite,
        'INDOOR' => WorkplaceType.indoor,
        _ => WorkplaceType.other,
      };

  String toApi() => switch (this) {
        WorkplaceType.office => 'OFFICE',
        WorkplaceType.largeSite => 'LARGE_SITE',
        WorkplaceType.constructionSite => 'CONSTRUCTION_SITE',
        WorkplaceType.indoor => 'INDOOR',
        WorkplaceType.other => 'OTHER',
      };

  String get label => switch (this) {
        WorkplaceType.office => '일반 사무실',
        WorkplaceType.largeSite => '대형 사업장',
        WorkplaceType.constructionSite => '건설 현장',
        WorkplaceType.indoor => '지하·실내',
        WorkplaceType.other => '기타',
      };
}

/// 관리자웹 근무지 관리 화면과 동일한 필드를 갖는 상세 근무지 모델.
/// 홈 화면 지오펜스 판정용 최소 모델(`Workplace`)과는 별도로 관리한다.
class WorkplaceDetail {
  const WorkplaceDetail({
    required this.id,
    required this.companyId,
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
    this.validFrom,
    this.validTo,
    required this.active,
  });

  final int id;
  final int companyId;
  final String name;
  final String? address;
  final String? detailAddress;
  final WorkplaceType type;
  final double latitude;
  final double longitude;
  final int radiusMeters;
  final int? maxAccuracyMeters;
  final bool checkInAllowed;
  final bool checkOutAllowed;
  final String? validFrom;
  final String? validTo;
  final bool active;

  factory WorkplaceDetail.fromJson(Map<String, dynamic> json) => WorkplaceDetail(
        id: json['id'] as int,
        companyId: json['companyId'] as int,
        name: json['name'] as String,
        address: json['address'] as String?,
        detailAddress: json['detailAddress'] as String?,
        type: WorkplaceTypeX.fromApi(json['type'] as String),
        latitude: (json['latitude'] as num).toDouble(),
        longitude: (json['longitude'] as num).toDouble(),
        radiusMeters: json['radiusMeters'] as int,
        maxAccuracyMeters: json['maxAccuracyMeters'] as int?,
        checkInAllowed: json['checkInAllowed'] as bool,
        checkOutAllowed: json['checkOutAllowed'] as bool,
        validFrom: json['validFrom'] as String?,
        validTo: json['validTo'] as String?,
        active: json['active'] as bool,
      );
}

class WorkplaceDetailPayload {
  const WorkplaceDetailPayload({
    required this.companyId,
    required this.name,
    required this.address,
    this.detailAddress,
    required this.type,
    required this.latitude,
    required this.longitude,
    required this.radiusMeters,
    this.maxAccuracyMeters,
    required this.checkInAllowed,
    required this.checkOutAllowed,
    this.validFrom,
    this.validTo,
  });

  final int companyId;
  final String name;
  final String address;
  final String? detailAddress;
  final WorkplaceType type;
  final double latitude;
  final double longitude;
  final int radiusMeters;
  final int? maxAccuracyMeters;
  final bool checkInAllowed;
  final bool checkOutAllowed;
  final String? validFrom;
  final String? validTo;

  Map<String, dynamic> toJson() => {
        'companyId': companyId,
        'name': name,
        'address': address,
        if (detailAddress != null && detailAddress!.isNotEmpty) 'detailAddress': detailAddress,
        'type': type.toApi(),
        'latitude': latitude,
        'longitude': longitude,
        'radiusMeters': radiusMeters,
        if (maxAccuracyMeters != null) 'maxAccuracyMeters': maxAccuracyMeters,
        'checkInAllowed': checkInAllowed,
        'checkOutAllowed': checkOutAllowed,
        if (validFrom != null && validFrom!.isNotEmpty) 'validFrom': validFrom,
        if (validTo != null && validTo!.isNotEmpty) 'validTo': validTo,
      };
}
