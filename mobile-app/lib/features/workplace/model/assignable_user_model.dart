/// 근무지 직원 배정 관리 화면에서 쓰는 최소 사용자 정보.
class AssignableUser {
  const AssignableUser({
    required this.id,
    required this.name,
    required this.employeeNumber,
    required this.status,
  });

  final int id;
  final String name;
  final String employeeNumber;
  final String status;

  factory AssignableUser.fromJson(Map<String, dynamic> json) => AssignableUser(
        id: json['id'] as int,
        name: json['name'] as String,
        employeeNumber: json['employeeNumber'] as String? ?? '',
        status: json['status'] as String? ?? 'ACTIVE',
      );
}
