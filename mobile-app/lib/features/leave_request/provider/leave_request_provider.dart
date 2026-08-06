import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/leave_request_model.dart';
import '../repository/leave_request_repository.dart';

final leaveRequestRepositoryProvider = Provider<LeaveRequestRepository>(
  (_) => LeaveRequestRepository(),
);

final myLeaveRequestsProvider = FutureProvider<List<LeaveRequestItem>>((ref) async {
  final repo = ref.read(leaveRequestRepositoryProvider);
  return repo.getMyRequests();
});

class SubmitLeaveRequestNotifier extends StateNotifier<AsyncValue<void>> {
  SubmitLeaveRequestNotifier(this._repo) : super(const AsyncValue.data(null));

  final LeaveRequestRepository _repo;

  Future<bool> submit(LeaveRequestSubmit request) async {
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

final submitLeaveRequestProvider =
    StateNotifierProvider<SubmitLeaveRequestNotifier, AsyncValue<void>>((ref) {
  final repo = ref.read(leaveRequestRepositoryProvider);
  return SubmitLeaveRequestNotifier(repo);
});
