import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/workplace_model.dart';
import '../repository/workplace_repository.dart';

final workplaceRepositoryProvider =
    Provider<WorkplaceRepository>((_) => WorkplaceRepository());

final assignedWorkplacesProvider =
    FutureProvider.autoDispose<List<Workplace>>((ref) {
  final repo = ref.read(workplaceRepositoryProvider);
  return repo.getAssignedWorkplaces();
});
