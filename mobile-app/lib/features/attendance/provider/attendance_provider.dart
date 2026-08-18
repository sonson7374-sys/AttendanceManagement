import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import '../model/attendance_model.dart';
import '../repository/attendance_repository.dart';
import '../../../core/device/device_repository.dart';
import '../../../core/location/location_service.dart';
import '../../workplace/model/workplace_model.dart';
import '../../workplace/provider/workplace_provider.dart';
import '../../workplace/repository/workplace_repository.dart';

/// 배정된 근무지 중 현재 위치와 가장 가까운 근무지와 거리를 반환한다.
/// 최종 지오펜스 판정은 서버가 재계산하며, 이 값은 화면 표시와 요청 필수 필드를 위한 것이다.
class NearestWorkplace {
  const NearestWorkplace({required this.workplace, required this.distanceMeters});
  final Workplace workplace;
  final double distanceMeters;
}

Future<NearestWorkplace> resolveNearestWorkplace(
  WorkplaceRepository workplaceRepo,
  Position position,
) async {
  final workplaces = await workplaceRepo.getAssignedWorkplaces();
  return nearestWorkplaceOf(workplaces, position);
}

/// [resolveNearestWorkplace]의 순수 계산 부분만 분리한 것 — 배정 근무지 목록을 이미
/// 갖고 있을 때(예: 홈 화면의 10초 위치 폴링이 목록을 캐시해둔 경우) 네트워크 재조회 없이
/// 재사용하기 위함이다.
NearestWorkplace nearestWorkplaceOf(List<Workplace> workplaces, Position position) {
  if (workplaces.isEmpty) {
    throw Exception('배정된 근무지가 없습니다. 관리자에게 문의해주세요.');
  }
  Workplace nearest = workplaces.first;
  double nearestDistance = double.infinity;
  for (final wp in workplaces) {
    final distance = Geolocator.distanceBetween(
      position.latitude, position.longitude, wp.latitude, wp.longitude,
    );
    if (distance < nearestDistance) {
      nearestDistance = distance;
      nearest = wp;
    }
  }
  return NearestWorkplace(workplace: nearest, distanceMeters: nearestDistance);
}

final attendanceRepositoryProvider = Provider<AttendanceRepository>(
  (_) => AttendanceRepository(),
);

// 오늘 근태 상태
class TodayAttendanceNotifier
    extends StateNotifier<AsyncValue<TodayAttendance?>> {
  TodayAttendanceNotifier(this._repo, this._workplaceRepo, this._deviceRepo)
      : super(const AsyncValue.loading()) {
    load();
  }

  final AttendanceRepository _repo;
  final WorkplaceRepository _workplaceRepo;
  final DeviceRepository _deviceRepo;

  /// [silent]이 true면 화면에 로딩 스피너를 띄우지 않고 조용히 갱신한다.
  /// 백그라운드 주기 폴링에서 매번 카드가 깜빡이는 것을 막기 위함이다.
  Future<void> load({bool silent = false}) async {
    try {
      if (!silent) state = const AsyncValue.loading();
      final result = await _repo.getToday();
      state = AsyncValue.data(result);
    } catch (e, st) {
      if (!silent) state = AsyncValue.error(e, st);
    }
  }

  Future<void> checkIn() async {
    final position = await getCurrentPosition();
    final nearest = await resolveNearestWorkplace(_workplaceRepo, position);
    final deviceId = await _deviceRepo.getOrCreateDeviceId();
    final result = await _repo.checkIn(CheckInRequest(
      workplaceId: nearest.workplace.id,
      latitude: position.latitude,
      longitude: position.longitude,
      accuracyMeters: position.accuracy,
      capturedAt: DateTime.now(),
      deviceId: deviceId,
      devicePlatform: _deviceRepo.devicePlatform,
      mockLocationDetected: position.isMocked,
    ));
    state = AsyncValue.data(result);
  }

  Future<void> checkOut() async {
    final position = await getCurrentPosition();
    final deviceId = await _deviceRepo.getOrCreateDeviceId();
    final result = await _repo.checkOut(CheckOutRequest(
      latitude: position.latitude,
      longitude: position.longitude,
      accuracyMeters: position.accuracy,
      capturedAt: DateTime.now(),
      deviceId: deviceId,
      devicePlatform: _deviceRepo.devicePlatform,
      mockLocationDetected: position.isMocked,
    ));
    state = AsyncValue.data(result);
  }

  Future<void> startBreak() async {
    final result = await _repo.startBreak();
    state = AsyncValue.data(result);
  }

  Future<void> endBreak() async {
    final result = await _repo.endBreak();
    state = AsyncValue.data(result);
  }
}

final todayAttendanceProvider = StateNotifierProvider<
    TodayAttendanceNotifier, AsyncValue<TodayAttendance?>>((ref) {
  final repo = ref.read(attendanceRepositoryProvider);
  final workplaceRepo = ref.read(workplaceRepositoryProvider);
  final deviceRepo = DeviceRepository();
  return TodayAttendanceNotifier(repo, workplaceRepo, deviceRepo);
});

// 월별 근태 내역
// autoDispose: 다른 탭으로 이동했다 돌아오면 캐시 대신 최신 데이터를 다시 조회한다.
// 근태 수정 요청이 승인되어 서버 데이터가 바뀐 뒤에도 앱에 반영되도록 하기 위함.
final monthlyRecordsProvider =
    FutureProvider.autoDispose.family<List<AttendanceRecord>, (int, int)>(
  (ref, args) async {
    final repo = ref.read(attendanceRepositoryProvider);
    final (year, month) = args;
    return repo.getRecords(year: year, month: month);
  },
);
