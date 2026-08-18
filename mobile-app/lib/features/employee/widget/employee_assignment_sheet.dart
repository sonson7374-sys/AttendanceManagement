import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/employee_model.dart';
import '../provider/employee_provider.dart';
import '../../work_schedule/provider/work_schedule_provider.dart';
import '../../workplace/provider/workplace_management_provider.dart';
import '../../../core/network/dio_client.dart';

/// 관리자웹 AssignmentModal과 동일. 근무제 배정(단일 선택)과 근무지 배정(다중 체크박스)을
/// 하나의 시트에서 처리한다.
class EmployeeAssignmentSheet extends ConsumerStatefulWidget {
  const EmployeeAssignmentSheet({super.key, required this.employee});

  final Employee employee;

  @override
  ConsumerState<EmployeeAssignmentSheet> createState() => _EmployeeAssignmentSheetState();
}

class _EmployeeAssignmentSheetState extends ConsumerState<EmployeeAssignmentSheet> {
  int? _selectedScheduleId;
  bool _savingSchedule = false;
  final Set<int> _pendingWorkplaceIds = {};

  Future<void> _changeSchedule() async {
    if (_selectedScheduleId == null) return;
    setState(() => _savingSchedule = true);
    try {
      await ref.read(employeeRepositoryProvider).assignWorkScheduleToUser(widget.employee.id, _selectedScheduleId!);
      ref.invalidate(userCurrentScheduleProvider(widget.employee.id));
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('근무제가 변경되었습니다.')));
      }
    } on ApiException catch (e) {
      _showError(e.message);
    } finally {
      if (mounted) setState(() => _savingSchedule = false);
    }
  }

  Future<void> _toggleWorkplace(int workplaceId, bool assign) async {
    setState(() => _pendingWorkplaceIds.add(workplaceId));
    try {
      final repo = ref.read(workplaceManagementRepositoryProvider);
      if (assign) {
        await repo.assignUserToWorkplace(workplaceId, widget.employee.id);
      } else {
        await repo.removeUserFromWorkplace(workplaceId, widget.employee.id);
      }
      ref.invalidate(userWorkplacesProvider(widget.employee.id));
    } on ApiException catch (e) {
      _showError(e.message);
    } finally {
      if (mounted) setState(() => _pendingWorkplaceIds.remove(workplaceId));
    }
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final currentScheduleAsync = ref.watch(userCurrentScheduleProvider(widget.employee.id));
    final allSchedulesAsync = ref.watch(workScheduleListProvider(false));
    final userWorkplacesAsync = ref.watch(userWorkplacesProvider(widget.employee.id));
    final allWorkplacesAsync = ref.watch(allCompanyWorkplacesProvider);

    return DraggableScrollableSheet(
      initialChildSize: 0.85,
      minChildSize: 0.5,
      maxChildSize: 0.95,
      expand: false,
      builder: (context, scrollController) {
        return Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('직원 배정 관리', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
              Text('${widget.employee.name} (${widget.employee.employeeNumber})', style: Theme.of(context).textTheme.bodyMedium),
              const SizedBox(height: 16),
              Expanded(
                child: ListView(
                  controller: scrollController,
                  children: [
                    Text('근무제', style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 8),
                    currentScheduleAsync.when(
                      loading: () => const LinearProgressIndicator(),
                      error: (e, _) => Text('현재 근무제를 불러오지 못했습니다.\n$e'),
                      data: (current) => Text('현재: ${current?.name ?? '배정 없음'}', style: const TextStyle(color: Colors.grey)),
                    ),
                    const SizedBox(height: 8),
                    allSchedulesAsync.when(
                      loading: () => const Center(child: CircularProgressIndicator()),
                      error: (e, _) => Text('근무제 목록을 불러오지 못했습니다.\n$e'),
                      data: (schedules) => Row(
                        children: [
                          Expanded(
                            child: DropdownButtonFormField<int>(
                              value: _selectedScheduleId,
                              decoration: const InputDecoration(labelText: '근무제 선택', border: OutlineInputBorder()),
                              items: schedules
                                  .map((s) => DropdownMenuItem(value: s.id, child: Text(s.name)))
                                  .toList(),
                              onChanged: (v) => setState(() => _selectedScheduleId = v),
                            ),
                          ),
                          const SizedBox(width: 8),
                          FilledButton(
                            onPressed: (_selectedScheduleId == null || _savingSchedule) ? null : _changeSchedule,
                            child: Text(_savingSchedule ? '변경 중...' : '변경'),
                          ),
                        ],
                      ),
                    ),
                    const Divider(height: 32),
                    Text('근무지', style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 8),
                    if (allWorkplacesAsync.isLoading || userWorkplacesAsync.isLoading)
                      const Center(child: CircularProgressIndicator())
                    else if (allWorkplacesAsync.hasError)
                      Text('근무지 목록을 불러오지 못했습니다.\n${allWorkplacesAsync.error}')
                    else if (userWorkplacesAsync.hasError)
                      Text('배정된 근무지를 불러오지 못했습니다.\n${userWorkplacesAsync.error}')
                    else
                      Builder(builder: (context) {
                        final allWorkplaces = allWorkplacesAsync.value!;
                        final assignedIds = userWorkplacesAsync.value!.map((w) => w.id).toSet();
                        return Column(
                          children: allWorkplaces.map((w) {
                            final checked = assignedIds.contains(w.id);
                            final pending = _pendingWorkplaceIds.contains(w.id);
                            return CheckboxListTile(
                              value: checked,
                              onChanged: pending ? null : (v) => _toggleWorkplace(w.id, v ?? false),
                              title: Text(w.name),
                              subtitle: Text(w.address ?? ''),
                              controlAffinity: ListTileControlAffinity.leading,
                              secondary: pending
                                  ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                                  : null,
                            );
                          }).toList(),
                        );
                      }),
                  ],
                ),
              ),
              const SizedBox(height: 8),
              OutlinedButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text('닫기'),
              ),
            ],
          ),
        );
      },
    );
  }
}
