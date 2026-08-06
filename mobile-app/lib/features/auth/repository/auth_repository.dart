import 'dart:async';

import 'package:dio/dio.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/device/device_repository.dart';
import '../../../core/network/dio_client.dart';
import '../../../core/storage/secure_storage.dart';
import '../model/auth_model.dart';

class AuthRepository {
  final Dio _dio = DioClient.instance;
  final DeviceRepository _deviceRepository = DeviceRepository();

  Future<AuthResponse> login(LoginRequest request) async {
    try {
      final response = await _dio.post(
        ApiConstants.login,
        data: request.toJson(),
      );
      final authResponse = AuthResponse.fromJson(
        response.data['data'] as Map<String, dynamic>,
      );
      await SecureStorage.saveTokens(
        accessToken: authResponse.accessToken,
        refreshToken: authResponse.refreshToken,
      );
      await SecureStorage.saveUserInfo(
        userId: authResponse.userId.toString(),
        email: authResponse.email,
        name: authResponse.name,
        role: authResponse.role,
        level: authResponse.level,
      );
      unawaited(_deviceRepository.registerCurrentDevice());
      return authResponse;
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<void> logout() async {
    try {
      await _dio.post(ApiConstants.logout);
    } on DioException {
      // 서버 로그아웃 실패해도 로컬 토큰은 삭제
    } finally {
      await SecureStorage.clear();
    }
  }

  Future<bool> isLoggedIn() async {
    final token = await SecureStorage.getAccessToken();
    return token != null;
  }
}
