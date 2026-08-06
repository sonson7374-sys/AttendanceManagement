import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/biometric/biometric_service.dart';
import '../../../core/storage/secure_storage.dart';

final biometricServiceProvider =
    Provider<BiometricService>((_) => BiometricService());

/// 생체인증 로그인 활성화 여부 (기기에 영구 저장된 사용자 선택값).
final biometricEnabledProvider = FutureProvider<bool>((ref) {
  return SecureStorage.isBiometricEnabled();
});

/// 이번 앱 세션에서 생체인증 잠금을 해제했는지 여부. 재시작하면 다시 false로 초기화된다.
class BiometricUnlockNotifier extends StateNotifier<bool> {
  BiometricUnlockNotifier() : super(false);
  void unlock() => state = true;
  void lock() => state = false;
}

final biometricUnlockProvider =
    StateNotifierProvider<BiometricUnlockNotifier, bool>(
        (ref) => BiometricUnlockNotifier());
