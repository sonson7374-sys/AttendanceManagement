import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/change_request_model.dart';
import '../repository/change_request_repository.dart';

final changeRequestRepositoryProvider = Provider<ChangeRequestRepository>(
  (_) => ChangeRequestRepository(),
);

// autoDispose: 탭을 벗어났다 돌아오면 캐시된 상태(예: 검토중) 대신 최신 상태를
// 다시 조회한다. 관리자 승인/반려 후 앱에 반영되려면 재조회가 필요하기 때문.
final myChangeRequestsProvider =
    FutureProvider.autoDispose<List<ChangeRequest>>((ref) async {
  final repo = ref.read(changeRequestRepositoryProvider);
  return repo.getMyRequests();
});

class SubmitChangeRequestNotifier extends StateNotifier<AsyncValue<void>> {
  SubmitChangeRequestNotifier(this._repo) : super(const AsyncValue.data(null));

  final ChangeRequestRepository _repo;

  Future<bool> submit(ChangeRequestSubmit request) async {
    state = const AsyncValue.loading();
    try {
      await _repo.submit(request);
      state = const AsyncValue.data(null);
      return true;
    } catch (e, st) {
      state = AsyncValue.error(e, st);
      return false;
    }
  }
}

final submitChangeRequestProvider = StateNotifierProvider<
    SubmitChangeRequestNotifier, AsyncValue<void>>((ref) {
  final repo = ref.read(changeRequestRepositoryProvider);
  return SubmitChangeRequestNotifier(repo);
});
