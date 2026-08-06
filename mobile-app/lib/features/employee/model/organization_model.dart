/// 관리자웹 Organization과 동일한 필드 구성. 직원 등록/수정 폼의 소속 조직 선택에 사용한다.
class Organization {
  Organization({
    required this.id,
    required this.companyId,
    this.parentId,
    required this.name,
    this.displayOrder,
    required this.active,
  });

  final int id;
  final int companyId;
  final int? parentId;
  final String name;
  final int? displayOrder;
  final bool active;

  factory Organization.fromJson(Map<String, dynamic> json) {
    return Organization(
      id: json['id'] as int,
      companyId: json['companyId'] as int,
      parentId: json['parentId'] as int?,
      name: json['name'] as String,
      displayOrder: json['displayOrder'] as int?,
      active: json['active'] as bool,
    );
  }
}
