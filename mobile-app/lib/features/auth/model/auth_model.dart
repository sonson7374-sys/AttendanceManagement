class LoginRequest {
  const LoginRequest({required this.email, required this.password});

  final String email;
  final String password;

  Map<String, dynamic> toJson() => {'email': email, 'password': password};
}

class AuthResponse {
  const AuthResponse({
    required this.accessToken,
    required this.refreshToken,
    required this.userId,
    required this.email,
    required this.name,
    required this.role,
    required this.level,
  });

  final String accessToken;
  final String refreshToken;
  final int userId;
  final String email;
  final String name;
  final String role;
  final String level;

  factory AuthResponse.fromJson(Map<String, dynamic> json) => AuthResponse(
        accessToken: json['accessToken'] as String,
        refreshToken: json['refreshToken'] as String,
        userId: json['userId'] as int,
        email: json['email'] as String,
        name: json['name'] as String,
        role: json['role'] as String,
        level: json['level'] as String,
      );
}
