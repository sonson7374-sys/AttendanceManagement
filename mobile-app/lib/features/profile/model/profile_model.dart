class UserProfile {
  const UserProfile({
    required this.id,
    required this.email,
    required this.name,
    required this.role,
    required this.status,
    this.employeeNumber,
    this.phone,
    this.jobTitle,
    this.employmentType,
    this.hireDate,
  });

  final int id;
  final String email;
  final String name;
  final String role;
  final String status;
  final String? employeeNumber;
  final String? phone;
  final String? jobTitle;
  final String? employmentType;
  final String? hireDate;

  factory UserProfile.fromJson(Map<String, dynamic> json) => UserProfile(
        id: json['id'] as int,
        email: json['email'] as String,
        name: json['name'] as String,
        role: json['role'] as String,
        status: json['status'] as String,
        employeeNumber: json['employeeNumber'] as String?,
        phone: json['phone'] as String?,
        jobTitle: json['jobTitle'] as String?,
        employmentType: json['employmentType'] as String?,
        hireDate: json['hireDate'] as String?,
      );
}
