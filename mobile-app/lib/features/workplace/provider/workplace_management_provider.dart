import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/assignable_user_model.dart';
import '../model/workplace_change_request_model.dart';
import '../model/workplace_detail_model.dart';
import '../repository/workplace_change_request_repository.dart';
import '../repository/workplace_management_repository.dart';

final workplaceManagementRepositoryProvider =
    Provider<WorkplaceManagementRepository>((_) => WorkplaceManagementRepository());

final workplaceChangeRequestRepositoryProvider =
    Provider<WorkplaceChangeRequestRepository>((_) => WorkplaceChangeRequestRepository());

/// 관리자웹과 달리 모바일 근무지 관리 화면은 회사 전체 근무지가 아니라, 로그인한 사용자
/// 본인에게 배정된 근무지만 보여준다(SYSTEM_ADMIN도 예외 없음). 관리(수정/배정/삭제) 권한은
/// 역할에 따라 화면에서 그대로 적용되므로, 여기서는 목록의 범위만 좁힌다.
final workplaceListProvider = FutureProvider.autoDispose<List<WorkplaceDetail>>((ref) {
  final repo = ref.read(workplaceManagementRepositoryProvider);
  return repo.getMyAssignedWorkplaceDetails();
});

/// 회사 전체 근무지 목록 — 직원 배정 관리 시트에서 "어느 근무지에 배정할지" 고를 때 쓴다.
/// [workplaceListProvider]와 달리 역할과 무관하게 항상 회사 전체를 조회한다(서버가
/// MANAGER 이상만 호출 가능하도록 검증).
final allCompanyWorkplacesProvider = FutureProvider.autoDispose<List<WorkplaceDetail>>((ref) {
  final repo = ref.read(workplaceManagementRepositoryProvider);
  return repo.getWorkplaces();
});

final myWorkplaceChangeRequestsProvider =
    FutureProvider.autoDispose<List<WorkplaceChangeRequest>>((ref) {
  final repo = ref.read(workplaceChangeRequestRepositoryProvider);
  return repo.getMyRequests();
});

final assignedUsersProvider =
    FutureProvider.autoDispose.family<List<AssignableUser>, int>((ref, workplaceId) {
  final repo = ref.read(workplaceManagementRepositoryProvider);
  return repo.getAssignedUsers(workplaceId);
});

final activeUsersProvider = FutureProvider.autoDispose<List<AssignableUser>>((ref) {
  final repo = ref.read(workplaceManagementRepositoryProvider);
  return repo.getActiveUsers();
});
