import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/organization_model.dart';
import '../model/user_device_model.dart';
import '../repository/employee_repository.dart';
import '../../work_schedule/model/work_schedule_model.dart';
import '../../workplace/model/workplace_detail_model.dart';

final employeeRepositoryProvider = Provider<EmployeeRepository>((ref) => EmployeeRepository());

final employeeListProvider = FutureProvider.autoDispose.family<EmployeePage, int>((ref, page) {
  final repo = ref.read(employeeRepositoryProvider);
  return repo.getUsers(page: page);
});

final organizationsProvider = FutureProvider.autoDispose<List<Organization>>((ref) {
  final repo = ref.read(employeeRepositoryProvider);
  return repo.getOrganizations();
});

final userDevicesProvider = FutureProvider.autoDispose.family<List<UserDevice>, int>((ref, userId) {
  final repo = ref.read(employeeRepositoryProvider);
  return repo.listDevices(userId);
});

final userWorkplacesProvider = FutureProvider.autoDispose.family<List<WorkplaceDetail>, int>((ref, userId) {
  final repo = ref.read(employeeRepositoryProvider);
  return repo.getWorkplacesForUser(userId);
});

final userCurrentScheduleProvider = FutureProvider.autoDispose.family<WorkSchedule?, int>((ref, userId) {
  final repo = ref.read(employeeRepositoryProvider);
  return repo.getCurrentWorkScheduleForUser(userId);
});
