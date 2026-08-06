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

const int kCompanyId = 1;

/// (전체근무지 목록 여부, 비활성 포함 여부)에 따라 관리자용 전체 목록 또는 본인 배정 근무지만 조회한다.
final workplaceListProvider =
    FutureProvider.autoDispose.family<List<WorkplaceDetail>, (bool isPlainEmployee, bool includeInactive)>((ref, args) {
  final (isPlainEmployee, includeInactive) = args;
  final repo = ref.read(workplaceManagementRepositoryProvider);
  return isPlainEmployee
      ? repo.getMyAssignedWorkplaceDetails()
      : repo.getWorkplaces(kCompanyId, includeInactive: includeInactive);
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
