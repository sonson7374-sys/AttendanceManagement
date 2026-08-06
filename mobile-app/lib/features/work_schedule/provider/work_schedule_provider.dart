import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/work_schedule_change_request_model.dart';
import '../model/work_schedule_model.dart';
import '../repository/work_schedule_change_request_repository.dart';
import '../repository/work_schedule_repository.dart';

final workScheduleRepositoryProvider = Provider<WorkScheduleRepository>((ref) => WorkScheduleRepository());

final workScheduleChangeRequestRepositoryProvider =
    Provider<WorkScheduleChangeRequestRepository>((ref) => WorkScheduleChangeRequestRepository());

/// isPlainEmployee=true면 본인 근무제(단건)만, false면 회사 전체 근무제 목록을 조회한다.
final workScheduleListProvider = FutureProvider.autoDispose.family<List<WorkSchedule>, bool>((ref, isPlainEmployee) {
  final repo = ref.read(workScheduleRepositoryProvider);
  return isPlainEmployee ? repo.getMyWorkSchedule() : repo.getWorkSchedules();
});

/// 변경요청 대상 선택용 활성 근무제 목록(관리자웹 getWorkScheduleOptions와 동일).
final workScheduleOptionsProvider = FutureProvider.autoDispose<List<WorkSchedule>>((ref) {
  final repo = ref.read(workScheduleRepositoryProvider);
  return repo.getWorkScheduleOptions();
});

final myWorkScheduleChangeRequestsProvider = FutureProvider.autoDispose<List<WorkScheduleChangeRequest>>((ref) {
  final repo = ref.read(workScheduleChangeRequestRepositoryProvider);
  return repo.getMyRequests();
});
