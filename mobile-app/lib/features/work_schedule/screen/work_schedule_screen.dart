import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/work_schedule_change_request_model.dart';
import '../model/work_schedule_model.dart';
import '../provider/work_schedule_provider.dart';
import '../widget/work_schedule_card.dart';
import '../widget/work_schedule_change_request_sheet.dart';
import '../widget/work_schedule_form_sheet.dart';
import '../../menu/provider/menu_permission_provider.dart';
import '../../menu/widget/app_menu_drawer.dart';
import '../../menu/widget/app_menu_leading_button.dart';
import '../../../core/network/dio_client.dart';
import '../../../core/storage/secure_storage.dart';

/// 근무제 관리 화면. 관리자웹 WorkSchedulesPage와 동일한 권한 규칙을 따른다:
/// - EMPLOYEE는 본인에게 배정된 근무제 1건만 보고, 등록·수정·삭제는 노출되지 않으며
///   변경요청(기존 활성 근무제 중 선택)만 가능하다.
/// - 등록/수정은 menu_permissions의 work-schedules:CREATE/EDIT 설정과 EMPLOYEE가 아님을 함께 확인한다.
/// - 삭제는 관리자웹과 동일하게 EMPLOYEE가 아니면 가능하고, 기본 근무제는 삭제할 수 없다.
///   (근무지와 달리 활성화/복구 API가 없어 삭제는 단방향이며 복구 버튼도 없다.)
class WorkScheduleScreen extends ConsumerStatefulWidget {
  const WorkScheduleScreen({super.key});

  @override
  ConsumerState<WorkScheduleScreen> createState() => _WorkScheduleScreenState();
}

class _WorkScheduleScreenState extends ConsumerState<WorkScheduleScreen> {
  bool _loadingUser = true;
  String? _role;

  @override
  void initState() {
    super.initState();
    _loadUser();
  }

  Future<void> _loadUser() async {
    final info = await SecureStorage.getUserInfo();
    if (!mounted) return;
    setState(() {
      _role = info['role'];
      _loadingUser = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      drawer: const AppMenuDrawer(),
      appBar: AppBar(
        leading: const AppMenuLeadingButton(),
        title: const Text('근무제 관리'),
      ),
      body: _loadingUser ? const Center(child: CircularProgressIndicator()) : _buildBody(context),
    );
  }

  Widget _buildBody(BuildContext context) {
    final isPlainEmployee = _role == 'EMPLOYEE';
    final overridesAsync = ref.watch(menuPermissionListProvider);

    return overridesAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('권한 정보를 불러오지 못했습니다.\n$e')),
      data: (overrides) {
        final canCreate = !isPlainEmployee && isActionEnabled(overrides, 'work-schedules', 'CREATE');
        final canEdit = !isPlainEmployee && isActionEnabled(overrides, 'work-schedules', 'EDIT');
        final canDelete = !isPlainEmployee;

        final schedulesAsync = ref.watch(workScheduleListProvider(isPlainEmployee));
        final myRequestsAsync =
            isPlainEmployee ? ref.watch(myWorkScheduleChangeRequestsProvider) : const AsyncValue.data(<WorkScheduleChangeRequest>[]);

        return Column(
          children: [
            if (canCreate)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                child: Row(
                  children: [
                    const Spacer(),
                    FilledButton.icon(
                      onPressed: () => _openCreate(context),
                      icon: const Icon(Icons.add, size: 18),
                      label: const Text('근무제 등록'),
                    ),
                  ],
                ),
              ),
            Expanded(
              child: schedulesAsync.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (e, _) => Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text('근무제를 불러오지 못했습니다.\n$e', textAlign: TextAlign.center),
                  ),
                ),
                data: (schedules) {
                  final myRequests = myRequestsAsync.valueOrNull ?? [];
                  final pendingByScheduleId = <int, WorkScheduleChangeRequest>{};
                  final latestByScheduleId = <int, WorkScheduleChangeRequest>{};
                  for (final r in myRequests) {
                    final id = r.currentWorkScheduleId;
                    if (id == null) continue;
                    if (r.status == 'PENDING') pendingByScheduleId[id] = r;
                    final existing = latestByScheduleId[id];
                    if (existing == null || DateTime.parse(r.createdAt).isAfter(DateTime.parse(existing.createdAt))) {
                      latestByScheduleId[id] = r;
                    }
                  }

                  if (schedules.isEmpty) {
                    return const Center(child: Text('등록된 근무제가 없습니다.'));
                  }

                  return ListView.builder(
                    padding: const EdgeInsets.all(16),
                    itemCount: schedules.length,
                    itemBuilder: (context, i) {
                      final s = schedules[i];
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: WorkScheduleCard(
                          schedule: s,
                          canEdit: canEdit,
                          canDelete: canDelete,
                          canRequestChange: isPlainEmployee,
                          pendingChangeRequest: pendingByScheduleId[s.id],
                          latestChangeRequest: latestByScheduleId[s.id],
                          onEdit: () => _openEdit(context, s),
                          onDelete: () => _confirmDelete(context, s),
                          onRequestChange: () => _openChangeRequest(context, s),
                        ),
                      );
                    },
                  );
                },
              ),
            ),
          ],
        );
      },
    );
  }

  Future<void> _openCreate(BuildContext context) async {
    final changed = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => const WorkScheduleFormSheet(),
    );
    if (changed == true) _refreshSchedules();
  }

  Future<void> _openEdit(BuildContext context, WorkSchedule s) async {
    final changed = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => WorkScheduleFormSheet(schedule: s),
    );
    if (changed == true) _refreshSchedules();
  }

  Future<void> _openChangeRequest(BuildContext context, WorkSchedule s) async {
    final changed = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => WorkScheduleChangeRequestSheet(currentSchedule: s),
    );
    if (changed == true) _refreshSchedules();
  }

  Future<void> _confirmDelete(BuildContext context, WorkSchedule s) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('근무제 삭제'),
        content: Text('"${s.name}" 근무제를 삭제하시겠습니까?\n배정된 직원의 근태 판정에 더 이상 사용할 수 없게 됩니다.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('취소')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('삭제')),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await ref.read(workScheduleRepositoryProvider).deactivateWorkSchedule(s.id);
      _refreshSchedules();
    } on ApiException catch (e) {
      _showError(e.message);
    }
  }

  void _refreshSchedules() {
    final isPlainEmployee = _role == 'EMPLOYEE';
    ref.invalidate(workScheduleListProvider(isPlainEmployee));
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }
}
