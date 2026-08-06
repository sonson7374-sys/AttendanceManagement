import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/location/location_service.dart';
import '../../workplace/model/workplace_model.dart';
import '../../workplace/provider/workplace_provider.dart';
import 'attendance_provider.dart';

/// 홈 화면에 표시할 "현재 근무지와의 거리 / GPS 정확도" 상태.
/// 근태 기록과 무관하게 "위치 새로고침" 버튼으로 언제든 다시 조회할 수 있다.
class LiveLocationInfo {
  const LiveLocationInfo({
    required this.workplace,
    required this.distanceMeters,
    required this.accuracyMeters,
  });

  final Workplace workplace;
  final double distanceMeters;
  final double accuracyMeters;
}

class LiveLocationNotifier extends StateNotifier<AsyncValue<LiveLocationInfo?>> {
  LiveLocationNotifier(this._ref) : super(const AsyncValue.data(null));

  final Ref _ref;

  Future<void> refresh() async {
    state = const AsyncValue.loading();
    try {
      final position = await getCurrentPosition();
      final workplaceRepo = _ref.read(workplaceRepositoryProvider);
      final nearest = await resolveNearestWorkplace(workplaceRepo, position);
      state = AsyncValue.data(LiveLocationInfo(
        workplace: nearest.workplace,
        distanceMeters: nearest.distanceMeters,
        accuracyMeters: position.accuracy,
      ));
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }
}

final liveLocationProvider =
    StateNotifierProvider<LiveLocationNotifier, AsyncValue<LiveLocationInfo?>>(
  (ref) => LiveLocationNotifier(ref),
);
