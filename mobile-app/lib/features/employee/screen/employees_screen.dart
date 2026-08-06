import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/employee_model.dart';
import '../provider/employee_provider.dart';
import '../widget/employee_assignment_sheet.dart';
import '../widget/employee_card.dart';
import '../widget/employee_devices_sheet.dart';
import '../widget/employee_form_sheet.dart';
import '../../menu/provider/menu_permission_provider.dart';
import '../../menu/widget/app_menu_drawer.dart';
import '../../menu/widget/app_menu_leading_button.dart';
import '../../../core/network/dio_client.dart';
import '../../../core/storage/secure_storage.dart';

/// 직원 관리 화면. 관리자웹 EmployeesPage와 동일한 권한 규칙을 따른다:
/// - 목록/단건 조회는 인증만 있으면 되고, 서버가 조직 계층 기준으로 응답 범위를 좁힌다
///   (SYSTEM_ADMIN·HR_ADMIN 전체, MANAGER 하위 조직, EMPLOYEE는 본인만).
/// - 등록/수정은 menu_permissions의 employees:CREATE/EDIT 설정을 따른다(역할 하드 배제 없음).
/// - 역할 변경·비밀번호 직접 변경·잠금/잠금해제는 SYSTEM_ADMIN만 가능하다(고정, 권한설정 화면 대상 아님).
/// - 비밀번호 초기화·퇴사 처리는 HR_ADMIN 이상만 가능하다(고정).
class EmployeesScreen extends ConsumerStatefulWidget {
  const EmployeesScreen({super.key});

  @override
  ConsumerState<EmployeesScreen> createState() => _EmployeesScreenState();
}

class _EmployeesScreenState extends ConsumerState<EmployeesScreen> {
  bool _loadingUser = true;
  String? _role;
  int _page = 0;

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
        title: const Text('직원 관리'),
      ),
      body: _loadingUser ? const Center(child: CircularProgressIndicator()) : _buildBody(context),
    );
  }

  Widget _buildBody(BuildContext context) {
    final canChangeRole = _role == 'SYSTEM_ADMIN';
    final canManageEmployment = _role == 'HR_ADMIN' || _role == 'SYSTEM_ADMIN';
    final overridesAsync = ref.watch(menuPermissionListProvider);

    return overridesAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('권한 정보를 불러오지 못했습니다.\n$e')),
      data: (overrides) {
        final canCreate = isActionEnabled(overrides, 'employees', 'CREATE');
        final canEdit = isActionEnabled(overrides, 'employees', 'EDIT');
        final pageAsync = ref.watch(employeeListProvider(_page));

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
                      label: const Text('직원 등록'),
                    ),
                ],
              ),
            ),
            Expanded(
              child: pageAsync.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (e, _) => Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text('직원 목록을 불러오지 못했습니다.\n$e', textAlign: TextAlign.center),
                  ),
                ),
                data: (result) {
                  if (result.content.isEmpty) {
                    return const Center(child: Text('조회 가능한 직원이 없습니다.'));
                  }
                  return ListView.builder(
                    padding: const EdgeInsets.all(16),
                    itemCount: result.content.length,
                    itemBuilder: (context, i) {
                      final emp = result.content[i];
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: EmployeeCard(
                          employee: emp,
                          canEdit: canEdit,
                          canChangeRole: canChangeRole,
                          canManageEmployment: canManageEmployment,
                          onTap: () => _openAssignment(context, emp),
                          onEdit: () => _openEdit(context, emp, canChangeRole),
                          onToggleLock: () => _confirmToggleLock(context, emp),
                          onDevices: () => _openDevices(context, emp),
                          onResetPassword: () => _confirmResetPassword(context, emp),
                          onSetPassword: () => _openSetPassword(context, emp),
                          onResign: () => _confirmResign(context, emp),
                        ),
                      );
                    },
                  );
                },
              ),
            ),
            _buildPagination(),
          ],
        );
      },
    );
  }

  Widget _buildPagination() {
    final pageAsync = ref.watch(employeeListProvider(_page));
    final totalPages = pageAsync.valueOrNull?.totalPages ?? 1;
    if (totalPages <= 1) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.all(12),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          TextButton(onPressed: _page > 0 ? () => setState(() => _page = 0) : null, child: const Text('처음')),
          TextButton(onPressed: _page > 0 ? () => setState(() => _page -= 1) : null, child: const Text('이전')),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: Text('${_page + 1} / $totalPages'),
          ),
          TextButton(onPressed: _page + 1 < totalPages ? () => setState(() => _page += 1) : null, child: const Text('다음')),
        ],
      ),
    );
  }

  Future<void> _openCreate(BuildContext context) async {
    final changed = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => const EmployeeFormSheet(canChangeRole: true),
    );
    if (changed == true) _refresh();
  }

  Future<void> _openEdit(BuildContext context, Employee emp, bool canChangeRole) async {
    final changed = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => EmployeeFormSheet(employee: emp, canChangeRole: canChangeRole),
    );
    if (changed == true) _refresh();
  }

  Future<void> _openAssignment(BuildContext context, Employee emp) async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => EmployeeAssignmentSheet(employee: emp),
    );
  }

  Future<void> _openDevices(BuildContext context, Employee emp) async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => EmployeeDevicesSheet(employee: emp),
    );
  }

  Future<void> _confirmToggleLock(BuildContext context, Employee emp) async {
    final locking = emp.status != UserStatus.locked;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(locking ? '계정 잠금' : '계정 잠금 해제'),
        content: Text('"${emp.name}" 계정을 ${locking ? '잠그' : '잠금 해제하'}시겠습니까?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('취소')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: Text(locking ? '잠금' : '잠금 해제')),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      final repo = ref.read(employeeRepositoryProvider);
      if (locking) {
        await repo.lockUser(emp.id);
      } else {
        await repo.unlockUser(emp.id);
      }
      _refresh();
    } on ApiException catch (e) {
      _showError(e.message);
    }
  }

  Future<void> _confirmResetPassword(BuildContext context, Employee emp) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('비밀번호 초기화'),
        content: Text('"${emp.name}"의 비밀번호를 임시 비밀번호로 초기화하시겠습니까?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('취소')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('초기화')),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      final tempPassword = await ref.read(employeeRepositoryProvider).resetPassword(emp.id);
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('임시 비밀번호 발급됨'),
          content: SelectableText('$tempPassword\n\n직원에게 안전하게 전달해주세요.'),
          actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('확인'))],
        ),
      );
    } on ApiException catch (e) {
      _showError(e.message);
    }
  }

  Future<void> _openSetPassword(BuildContext context, Employee emp) async {
    final controller = TextEditingController();
    final newPassword = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('"${emp.name}" 비밀번호 변경'),
        content: TextField(
          controller: controller,
          obscureText: true,
          decoration: const InputDecoration(labelText: '새 비밀번호 (8자 이상)'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('취소')),
          FilledButton(onPressed: () => Navigator.pop(ctx, controller.text), child: const Text('변경')),
        ],
      ),
    );
    if (newPassword == null || newPassword.length < 8) {
      if (newPassword != null) _showError('비밀번호는 8자 이상이어야 합니다.');
      return;
    }
    try {
      await ref.read(employeeRepositoryProvider).setPassword(emp.id, newPassword);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('비밀번호가 변경되었습니다.')));
    } on ApiException catch (e) {
      _showError(e.message);
    }
  }

  Future<void> _confirmResign(BuildContext context, Employee emp) async {
    final now = DateTime.now();
    final picked = await showDatePicker(context: context, initialDate: now, firstDate: DateTime(now.year - 1), lastDate: DateTime(now.year + 1));
    if (picked == null) return;
    final resignDate = '${picked.year.toString().padLeft(4, '0')}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}';
    if (!context.mounted) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('퇴사 처리'),
        content: Text('"${emp.name}"을(를) $resignDate 자로 퇴사 처리하시겠습니까?\n더 이상 로그인할 수 없게 됩니다.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('취소')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('퇴사 처리')),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await ref.read(employeeRepositoryProvider).resignUser(emp.id, resignDate);
      _refresh();
    } on ApiException catch (e) {
      _showError(e.message);
    }
  }

  void _refresh() {
    ref.invalidate(employeeListProvider(_page));
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }
}
