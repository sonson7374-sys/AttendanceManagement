import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/auth_model.dart';
import '../repository/auth_repository.dart';
import 'biometric_provider.dart';
import '../../../core/network/dio_client.dart';
import '../../attendance/provider/attendance_provider.dart';
import '../../attendance/provider/location_provider.dart';
import '../../change_request/provider/change_request_provider.dart';
import '../../leave_request/provider/leave_request_provider.dart';
import '../../notification/provider/notification_provider.dart';
import '../../profile/provider/profile_provider.dart';

final authRepositoryProvider = Provider<AuthRepository>(
  (_) => AuthRepository(),
);

// 로그인 상태 (true = 로그인 됨)
final authStateProvider = FutureProvider<bool>((ref) async {
  final repo = ref.read(authRepositoryProvider);
  return repo.isLoggedIn();
});

/// 세션이 서버 쪽에서 무효화되어(재발급 실패) 강제로 로그아웃된 경우 true.
/// 로그인 화면이 이 값을 한 번 소비해 안내 메시지를 보여준다.
final sessionExpiredProvider = StateProvider<bool>((_) => false);

// 로그인/로그아웃 액션 Notifier
class AuthNotifier extends StateNotifier<AsyncValue<bool>> {
  AuthNotifier(this._repo, this._ref) : super(const AsyncValue.loading()) {
    DioClient.onSessionExpired = _forceLogout;
    _checkLoginState();
  }

  final AuthRepository _repo;
  final Ref _ref;

  Future<void> _checkLoginState() async {
    state = const AsyncValue.loading();
    final loggedIn = await _repo.isLoggedIn();
    state = AsyncValue.data(loggedIn);
  }

  Future<void> login(String email, String password) async {
    state = const AsyncValue.loading();
    DioClient.sessionEpoch++;
    try {
      await _repo.login(LoginRequest(email: email, password: password));
      _resetUserScopedProviders();
      state = const AsyncValue.data(true);
    } catch (e, st) {
      state = AsyncValue.error(e, st);
      rethrow; // login screen catch 블록에서 에러 메시지 표시
    }
  }

  Future<void> logout() async {
    DioClient.sessionEpoch++;
    await _repo.logout();
    _ref.read(biometricUnlockProvider.notifier).lock();
    _resetUserScopedProviders();
    state = const AsyncValue.data(false);
  }

  /// dio_client가 refresh token 재발급까지 실패해 로컬 세션을 이미 지운 뒤 호출한다.
  /// SecureStorage.clear()는 여기서 상태를 갱신하지 않으면 router가 이를 모르고
  /// 로그인 화면으로 리다이렉트하지 않아, 만료된 세션으로 API 호출이 계속 실패하는
  /// 화면에 사용자가 갇히게 된다.
  void _forceLogout() {
    DioClient.sessionEpoch++;
    _ref.read(biometricUnlockProvider.notifier).lock();
    _resetUserScopedProviders();
    _ref.read(sessionExpiredProvider.notifier).state = true;
    state = const AsyncValue.data(false);
  }

  /// 계정별 근태·위치·알림 상태는 앱 프로세스 전체에 걸쳐 캐시되므로, 로그인/로그아웃
  /// 시점에 무효화하지 않으면 같은 기기에서 다른 계정으로 전환했을 때 이전 사용자의
  /// 데이터(오늘 근태, 위치, 요청 목록 등)가 화면에 그대로 남아 보이는 문제가 생긴다.
  void _resetUserScopedProviders() {
    _ref.invalidate(todayAttendanceProvider);
    _ref.invalidate(liveLocationProvider);
    _ref.invalidate(monthlyRecordsProvider);
    _ref.invalidate(myChangeRequestsProvider);
    _ref.invalidate(myLeaveRequestsProvider);
    _ref.invalidate(notificationListProvider);
    _ref.invalidate(unreadNotificationCountProvider);
    _ref.invalidate(myProfileProvider);
  }
}

final authNotifierProvider =
    StateNotifierProvider<AuthNotifier, AsyncValue<bool>>((ref) {
  final repo = ref.read(authRepositoryProvider);
  return AuthNotifier(repo, ref);
});
