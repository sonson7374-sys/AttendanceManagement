import 'dart:io' show Platform;

import 'package:dio/dio.dart';
import 'package:uuid/uuid.dart';

import '../constants/api_constants.dart';
import '../network/dio_client.dart';
import '../storage/secure_storage.dart';

class DeviceRepository {
  final Dio _dio = DioClient.instance;

  /// 백엔드 devicePlatform 값('ANDROID' | 'IOS')과 일치시킨다.
  String get devicePlatform => Platform.isIOS ? 'IOS' : 'ANDROID';

  Future<String> getOrCreateDeviceId() async {
    final existing = await SecureStorage.getDeviceId();
    if (existing != null) return existing;
    final generated = const Uuid().v4();
    await SecureStorage.saveDeviceId(generated);
    return generated;
  }

  /// 로그인 직후 현재 기기를 서버에 등록(또는 갱신)한다.
  /// 등록 실패는 로그인 흐름을 막지 않는다.
  Future<void> registerCurrentDevice() async {
    try {
      final deviceId = await getOrCreateDeviceId();
      await _dio.post(ApiConstants.myDevices, data: {
        'deviceId': deviceId,
        'devicePlatform': devicePlatform,
      });
    } catch (_) {
      // 기기 등록 실패는 무시한다 (출퇴근 기능에 영향 없음)
    }
  }

  Future<List<Map<String, dynamic>>> listMyDevices() async {
    try {
      final response = await _dio.get(ApiConstants.myDevices);
      final list = response.data['data'] as List<dynamic>;
      return list.cast<Map<String, dynamic>>();
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }

  Future<void> revokeDevice(String deviceId) async {
    try {
      await _dio.delete('${ApiConstants.myDevices}/$deviceId');
    } on DioException catch (e) {
      throw ApiException.fromDioException(e);
    }
  }
}
