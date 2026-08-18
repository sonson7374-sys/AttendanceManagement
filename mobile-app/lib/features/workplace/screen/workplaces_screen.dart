import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/workplace_change_request_model.dart';
import '../model/workplace_detail_model.dart';
import '../provider/workplace_management_provider.dart';
import '../widget/assign_users_sheet.dart';
import '../widget/workplace_card.dart';
import '../widget/workplace_change_request_sheet.dart';
import '../widget/workplace_form_sheet.dart';
import '../../menu/provider/menu_permission_provider.dart';
import '../../menu/widget/app_menu_drawer.dart';
import '../../menu/widget/app_menu_leading_button.dart';
import '../../../core/network/dio_client.dart';
import '../../../core/storage/secure_storage.dart';

/// 근무지 관리 화면. 목록은 관리자웹과 달리 역할과 무관하게 항상 본인에게 배정된 근무지만
/// 보여준다(SYSTEM_ADMIN 포함) — [workplaceListProvider] 참고. 관리 권한은 관리자웹
/// WorkplacesPage와 동일한 규칙을 따른다:
/// - EMPLOYEE는 등록·수정·배정·삭제가 노출되지 않으며 변경요청만 가능하다.
/// - 등록/수정은 menu_permissions의 workplaces:CREATE/EDIT 설정과 EMPLOYEE가 아님을 함께 확인한다.
/// - 삭제(비활성화)/복구는 서버 정책과 동일하게 SYSTEM_ADMIN만 가능하다.
class WorkplacesScreen extends ConsumerStatefulWidget {
  const WorkplacesScreen({super.key});

  @override
  ConsumerState<WorkplacesScreen> createState() => _WorkplacesScreenState();
}

class _WorkplacesScreenState extends ConsumerState<WorkplacesScreen> {
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
        title: const Text('근무지 관리'),
      ),
      body: _loadingUser ? const Center(child: CircularProgressIndicator()) : _buildBody(context),
    );
  }

  Widget _buildBody(BuildContext context) {
    final isPlainEmployee = _role == 'EMPLOYEE';
    final canManage = _role == 'SYSTEM_ADMIN';
    final overridesAsync = ref.watch(menuPermissionListProvider);

    return overridesAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('권한 정보를 불러오지 못했습니다.\n$e')),
      data: (overrides) {
        final canCreate = !isPlainEmployee && isActionEnabled(overrides, 'workplaces', 'CREATE');
        final canEdit = !isPlainEmployee && isActionEnabled(overrides, 'workplaces', 'EDIT');

        final workplacesAsync = ref.watch(workplaceListProvider);
        final myRequestsAsync = isPlainEmployee ? ref.watch(myWorkplaceChangeRequestsProvider) : const AsyncValue.data(<WorkplaceChangeRequest>[]);

        return Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
              child: Row(
                children: [
                  const Spacer(),
                  if (canCreate)
                    FilledButton.icon(
                      onPressed: () => _openCreate(context),
                      icon: const Icon(Icons.add, size: 18),
                      label: const Text('근무지 등록'),
                    ),
                ],
              ),
            ),
            Expanded(
              child: workplacesAsync.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (e, _) => Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text('근무지를 불러오지 못했습니다.\n$e', textAlign: TextAlign.center),
                  ),
                ),
                data: (workplaces) {
                  final myRequests = myRequestsAsync.valueOrNull ?? [];
                  final pendingByWorkplaceId = <int, WorkplaceChangeRequest>{};
                  final latestByWorkplaceId = <int, WorkplaceChangeRequest>{};
                  for (final r in myRequests) {
                    final id = r.currentWorkplaceId;
                    if (id == null) continue;
                    if (r.status == 'PENDING') pendingByWorkplaceId[id] = r;
                    final existing = latestByWorkplaceId[id];
                    if (existing == null || DateTime.parse(r.createdAt).isAfter(DateTime.parse(existing.createdAt))) {
                      latestByWorkplaceId[id] = r;
                    }
                  }

                  if (workplaces.isEmpty) {
                    return const Center(child: Text('등록된 근무지가 없습니다.'));
                  }

                  return ListView.builder(
                    padding: const EdgeInsets.all(16),
                    itemCount: workplaces.length,
                    itemBuilder: (context, i) {
                      final w = workplaces[i];
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: WorkplaceCard(
                          workplace: w,
                          canManage: canManage,
                          canEdit: canEdit,
                          canRequestChange: isPlainEmployee,
                          pendingChangeRequest: pendingByWorkplaceId[w.id],
                          latestChangeRequest: latestByWorkplaceId[w.id],
                          onEdit: () => _openEdit(context, w),
                          onAssign: () => _openAssign(context, w),
                          onDelete: () => _confirmDelete(context, w),
                          onRestore: () => _confirmRestore(context, w),
                          onRequestChange: () => _openChangeRequest(context, w),
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
      builder: (ctx) => const WorkplaceFormSheet(),
    );
    if (changed == true) _refreshWorkplaces();
  }

  Future<void> _openEdit(BuildContext context, WorkplaceDetail w) async {
    final changed = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => WorkplaceFormSheet(workplace: w),
    );
    if (changed == true) _refreshWorkplaces();
  }

  Future<void> _openAssign(BuildContext context, WorkplaceDetail w) async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => AssignUsersSheet(workplace: w),
    );
  }

  Future<void> _openChangeRequest(BuildContext context, WorkplaceDetail w) async {
    final changed = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => WorkplaceChangeRequestSheet(currentWorkplace: w),
    );
    if (changed == true) _refreshWorkplaces();
  }

  Future<void> _confirmDelete(BuildContext context, WorkplaceDetail w) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('근무지 삭제'),
        content: Text('"${w.name}" 근무지를 삭제하시겠습니까?\n배정된 직원의 출퇴근에 더 이상 사용할 수 없게 됩니다.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('취소')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('삭제')),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await ref.read(workplaceManagementRepositoryProvider).deactivateWorkplace(w.id);
      _refreshWorkplaces();
    } on ApiException catch (e) {
      _showError(e.message);
    }
  }

  Future<void> _confirmRestore(BuildContext context, WorkplaceDetail w) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('근무지 복구'),
        content: Text('"${w.name}" 근무지를 복구하시겠습니까?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('취소')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('복구')),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await ref.read(workplaceManagementRepositoryProvider).activateWorkplace(w.id);
      _refreshWorkplaces();
    } on ApiException catch (e) {
      _showError(e.message);
    }
  }

  void _refreshWorkplaces() {
    ref.invalidate(workplaceListProvider);
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }
}
