/// 관리자웹 UserRole과 동일한 권한 역할.
enum UserRole { employee, manager, hrAdmin, systemAdmin }

extension UserRoleX on UserRole {
  static UserRole fromApi(String value) {
    switch (value) {
      case 'EMPLOYEE':
        return UserRole.employee;
      case 'MANAGER':
        return UserRole.manager;
      case 'HR_ADMIN':
        return UserRole.hrAdmin;
      case 'SYSTEM_ADMIN':
        return UserRole.systemAdmin;
      default:
        return UserRole.employee;
    }
  }

  String toApi() {
    switch (this) {
      case UserRole.employee:
        return 'EMPLOYEE';
      case UserRole.manager:
        return 'MANAGER';
      case UserRole.hrAdmin:
        return 'HR_ADMIN';
      case UserRole.systemAdmin:
        return 'SYSTEM_ADMIN';
    }
  }

  String get label {
    switch (this) {
      case UserRole.employee:
        return '직원';
      case UserRole.manager:
        return '관리자';
      case UserRole.hrAdmin:
        return 'HR 관리자';
      case UserRole.systemAdmin:
        return '시스템 관리자';
    }
  }
}

const List<UserRole> kUserRoleOptions = UserRole.values;

/// 관리자웹 UserStatus와 동일한 상태값.
enum UserStatus { active, inactive, locked }

extension UserStatusX on UserStatus {
  static UserStatus fromApi(String value) {
    switch (value) {
      case 'ACTIVE':
        return UserStatus.active;
      case 'INACTIVE':
        return UserStatus.inactive;
      case 'LOCKED':
        return UserStatus.locked;
      default:
        return UserStatus.active;
    }
  }

  String get label {
    switch (this) {
      case UserStatus.active:
        return '활성';
      case UserStatus.inactive:
        return '비활성';
      case UserStatus.locked:
        return '잠금';
    }
  }
}

/// 관리자웹 User 타입과 동일한 필드 구성.
class Employee {
  Employee({
    required this.id,
    required this.email,
    required this.name,
    required this.employeeNumber,
    this.phone,
    required this.companyId,
    this.organizationId,
    this.jobTitle,
    this.employmentType,
    this.hireDate,
    this.resignDate,
    this.defaultWorkplaceId,
    this.workScheduleId,
    required this.role,
    required this.level,
    required this.status,
  });

  final int id;
  final String email;
  final String name;
  final String employeeNumber;
  final String? phone;
  final int companyId;
  final int? organizationId;
  final String? jobTitle;
  final String? employmentType;
  final String? hireDate;
  final String? resignDate;
  final int? defaultWorkplaceId;
  final int? workScheduleId;
  final UserRole role;
  final String level;
  final UserStatus status;

  factory Employee.fromJson(Map<String, dynamic> json) {
    return Employee(
      id: json['id'] as int,
      email: json['email'] as String,
      name: json['name'] as String,
      employeeNumber: json['employeeNumber'] as String? ?? '',
      phone: json['phone'] as String?,
      companyId: json['companyId'] as int,
      organizationId: json['organizationId'] as int?,
      jobTitle: json['jobTitle'] as String?,
      employmentType: json['employmentType'] as String?,
      hireDate: json['hireDate'] as String?,
      resignDate: json['resignDate'] as String?,
      defaultWorkplaceId: json['defaultWorkplaceId'] as int?,
      workScheduleId: json['workScheduleId'] as int?,
      role: UserRoleX.fromApi(json['role'] as String),
      level: json['level'] as String? ?? '',
      status: UserStatusX.fromApi(json['status'] as String),
    );
  }
}

/// 관리자웹 EmployeesPage의 등록(Create) 폼과 동일. companyId는 관리자웹처럼 1로 고정한다.
class EmployeeCreatePayload {
  EmployeeCreatePayload({
    required this.email,
    required this.password,
    required this.name,
    required this.employeeNumber,
    required this.role,
    required this.level,
    this.organizationId,
  });

  final String email;
  final String password;
  final String name;
  final String employeeNumber;
  final UserRole role;
  final String level;
  final int? organizationId;

  Map<String, dynamic> toJson() => {
        'email': email,
        'password': password,
        'name': name,
        'employeeNumber': employeeNumber,
        'role': role.toApi(),
        'level': level,
        'companyId': 1,
        if (organizationId != null) 'organizationId': organizationId,
      };
}

/// 관리자웹 UpdateProfilePayload와 동일.
class EmployeeProfileUpdatePayload {
  EmployeeProfileUpdatePayload({
    required this.name,
    this.phone,
    this.jobTitle,
    this.employeeNumber,
    this.organizationId,
    this.employmentType,
    this.hireDate,
    required this.level,
  });

  final String name;
  final String? phone;
  final String? jobTitle;
  final String? employeeNumber;
  final int? organizationId;
  final String? employmentType;
  final String? hireDate;
  final String level;

  Map<String, dynamic> toJson() => {
        'name': name,
        if (phone != null) 'phone': phone,
        if (jobTitle != null) 'jobTitle': jobTitle,
        if (employeeNumber != null) 'employeeNumber': employeeNumber,
        'organizationId': organizationId,
        if (employmentType != null) 'employmentType': employmentType,
        if (hireDate != null) 'hireDate': hireDate,
        'level': level,
      };
}
