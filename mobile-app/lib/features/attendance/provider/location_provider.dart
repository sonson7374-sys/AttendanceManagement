import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/location/location_service.dart';
import '../../workplace/model/workplace_model.dart';
import '../../workplace/provider/workplace_provider.dart';
import 'attendance_provider.dart';

/// 홈 화면에 표시할 "현재 근무지와의 거리 / GPS 정확도" 상태 + 지도에 표시할 원본 좌표.
/// 근태 기록과 무관하게 "위치 새로고침" 버튼으로 언제든 다시 조회할 수 있다.
class LiveLocationInfo {
  const LiveLocationInfo({
    required this.workplace,
    required this.distanceMeters,
    required this.accuracyMeters,
    required this.latitude,
    required this.longitude,
  });

  final Workplace workplace;
  final double distanceMeters;
  final double accuracyMeters;
  final double latitude;
  final double longitude;
}

class LiveLocationNotifier extends StateNotifier<AsyncValue<LiveLocationInfo?>> {
  LiveLocationNotifier(this._ref) : super(const AsyncValue.data(null));

  final Ref _ref;

  // 배정 근무지 목록은 세션 동안 잘 안 바뀌므로, 실시간 위치 폴링(10초 간격)이 매번
  // 네트워크로 다시 가져오지 않도록 캐시해둔다. "위치 새로고침" 버튼 등 명시적 호출은
  // 항상 최신 목록을 다시 받는다(reuseCachedWorkplaces 기본값 false).
  List<Workplace>? _cachedWorkplaces;

  /// [reuseCachedWorkplaces]가 true면 배정 근무지 목록을 다시 조회하지 않고 캐시를 재사용한다
  /// (GPS 위치만 새로 조회) — 10초 폴링 전용. 이전 조회가 아직 끝나지 않았으면 이번 호출은
  /// 건너뛴다(GPS 조회가 최대 17초까지 걸릴 수 있어 폴링 tick과 겹칠 수 있기 때문).
  Future<void> refresh({bool reuseCachedWorkplaces = false}) async {
    if (state is AsyncLoading) return;
    state = const AsyncValue.loading();
    try {
      final position = await getCurrentPosition();
      final NearestWorkplace nearest;
      if (reuseCachedWorkplaces && _cachedWorkplaces != null) {
        nearest = nearestWorkplaceOf(_cachedWorkplaces!, position);
      } else {
        final workplaceRepo = _ref.read(workplaceRepositoryProvider);
        final workplaces = await workplaceRepo.getAssignedWorkplaces();
        _cachedWorkplaces = workplaces;
        nearest = nearestWorkplaceOf(workplaces, position);
      }
      state = AsyncValue.data(LiveLocationInfo(
        workplace: nearest.workplace,
        distanceMeters: nearest.distanceMeters,
        accuracyMeters: position.accuracy,
        latitude: position.latitude,
        longitude: position.longitude,
      ));
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  /// 10초 폴링 전용 — GPS 위치만 다시 재고, 배정 근무지 목록은 캐시를 재사용한다.
  Future<void> refreshPositionOnly() => refresh(reuseCachedWorkplaces: true);
}

final liveLocationProvider =
    StateNotifierProvider<LiveLocationNotifier, AsyncValue<LiveLocationInfo?>>(
  (ref) => LiveLocationNotifier(ref),
);
