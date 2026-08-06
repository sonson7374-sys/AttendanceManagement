import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../constants/api_constants.dart';

class SecureStorage {
  static const FlutterSecureStorage _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );

  static Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    await Future.wait([
      _storage.write(key: StorageKeys.accessToken, value: accessToken),
      _storage.write(key: StorageKeys.refreshToken, value: refreshToken),
    ]);
  }

  static Future<String?> getAccessToken() =>
      _storage.read(key: StorageKeys.accessToken);

  static Future<String?> getRefreshToken() =>
      _storage.read(key: StorageKeys.refreshToken);

  static Future<void> saveUserInfo({
    required String userId,
    required String email,
    required String name,
    String? role,
    String? level,
  }) async {
    await Future.wait([
      _storage.write(key: StorageKeys.userId, value: userId),
      _storage.write(key: StorageKeys.userEmail, value: email),
      _storage.write(key: StorageKeys.userName, value: name),
      if (role != null) _storage.write(key: StorageKeys.userRole, value: role),
      if (level != null) _storage.write(key: StorageKeys.userLevel, value: level),
    ]);
  }

  static Future<Map<String, String?>> getUserInfo() async {
    final results = await Future.wait([
      _storage.read(key: StorageKeys.userId),
      _storage.read(key: StorageKeys.userEmail),
      _storage.read(key: StorageKeys.userName),
      _storage.read(key: StorageKeys.userRole),
      _storage.read(key: StorageKeys.userLevel),
    ]);
    return {
      'userId': results[0],
      'email': results[1],
      'name': results[2],
      'role': results[3],
      'level': results[4],
    };
  }

  /// 로그아웃 시 세션 정보만 삭제한다. 기기 ID는 물리 기기 식별용이므로 보존한다.
  static Future<void> clear() async {
    await Future.wait([
      _storage.delete(key: StorageKeys.accessToken),
      _storage.delete(key: StorageKeys.refreshToken),
      _storage.delete(key: StorageKeys.userId),
      _storage.delete(key: StorageKeys.userEmail),
      _storage.delete(key: StorageKeys.userName),
      _storage.delete(key: StorageKeys.userRole),
      _storage.delete(key: StorageKeys.userLevel),
      _storage.delete(key: StorageKeys.biometricEnabled),
    ]);
  }

  static Future<String?> getDeviceId() =>
      _storage.read(key: StorageKeys.deviceId);

  static Future<void> saveDeviceId(String deviceId) =>
      _storage.write(key: StorageKeys.deviceId, value: deviceId);

  static Future<bool> isBiometricEnabled() async =>
      (await _storage.read(key: StorageKeys.biometricEnabled)) == 'true';

  static Future<void> setBiometricEnabled(bool enabled) =>
      _storage.write(key: StorageKeys.biometricEnabled, value: enabled.toString());

  /// 위치정보 수집 동의는 세션이 아닌 기기 단위 이력이므로 로그아웃 시에도 보존한다.
  static Future<bool> isLocationConsentGiven() async =>
      (await _storage.read(key: StorageKeys.locationConsentGiven)) == 'true';

  static Future<void> setLocationConsentGiven(bool given) =>
      _storage.write(key: StorageKeys.locationConsentGiven, value: given.toString());
}
