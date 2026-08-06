import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/assignable_user_model.dart';
import '../model/workplace_detail_model.dart';
import '../provider/workplace_management_provider.dart';
import '../../../core/network/dio_client.dart';

/// 근무지 직원 배정 관리. 관리자웹 AssignUserModal과 동일하게 배정/해제를 다루되,
/// 일괄 배정 모드는 모바일에서는 생략하고 단건 배정만 지원한다.
class AssignUsersSheet extends ConsumerStatefulWidget {
  const AssignUsersSheet({super.key, required this.workplace});

  final WorkplaceDetail workplace;

  @override
  ConsumerState<AssignUsersSheet> createState() => _AssignUsersSheetState();
}

class _AssignUsersSheetState extends ConsumerState<AssignUsersSheet> {
  int? _selectedUserId;
  bool _busy = false;

  Future<void> _assign() async {
    if (_selectedUserId == null) return;
    setState(() => _busy = true);
    try {
      await ref.read(workplaceManagementRepositoryProvider)
          .assignUserToWorkplace(widget.workplace.id, _selectedUserId!);
      ref.invalidate(assignedUsersProvider(widget.workplace.id));
      setState(() => _selectedUserId = null);
    } on ApiException catch (e) {
      _showError(e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _remove(int userId) async {
    setState(() => _busy = true);
    try {
      await ref.read(workplaceManagementRepositoryProvider)
          .removeUserFromWorkplace(widget.workplace.id, userId);
      ref.invalidate(assignedUsersProvider(widget.workplace.id));
    } on ApiException catch (e) {
      _showError(e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final assignedAsync = ref.watch(assignedUsersProvider(widget.workplace.id));
    final activeUsersAsync = ref.watch(activeUsersProvider);

    return DraggableScrollableSheet(
      initialChildSize: 0.75,
      minChildSize: 0.5,
      maxChildSize: 0.95,
      expand: false,
      builder: (context, scrollController) {
        return Padding(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('직원 배정', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
              Text(widget.workplace.name, style: Theme.of(context).textTheme.bodyMedium),
              const SizedBox(height: 16),
              assignedAsync.when(
                loading: () => const SizedBox.shrink(),
                error: (_, __) => const SizedBox.shrink(),
                data: (assigned) {
                  final assignedIds = assigned.map((u) => u.id).toSet();
                  return activeUsersAsync.when(
                    loading: () => const Padding(
                      padding: EdgeInsets.all(8),
                      child: LinearProgressIndicator(),
                    ),
                    error: (e, _) => Text('직원 목록을 불러오지 못했습니다: $e'),
                    data: (allUsers) {
                      final unassigned = allUsers
                          .where((u) => !assignedIds.contains(u.id) && u.status == 'ACTIVE')
                          .toList();
                      return Row(
                        children: [
                          Expanded(
                            child: DropdownButtonFormField<int>(
                              initialValue: _selectedUserId,
                              isExpanded: true,
                              decoration: const InputDecoration(labelText: '직원 선택', border: OutlineInputBorder()),
                              items: unassigned
                                  .map((u) => DropdownMenuItem(value: u.id, child: Text('${u.name} (${u.employeeNumber})')))
                                  .toList(),
                              onChanged: (v) => setState(() => _selectedUserId = v),
                            ),
                          ),
                          const SizedBox(width: 8),
                          FilledButton(
                            onPressed: (_selectedUserId == null || _busy) ? null : _assign,
                            child: const Text('배정'),
                          ),
                        ],
                      );
                    },
                  );
                },
              ),
              const SizedBox(height: 16),
              Text('현재 배정 직원', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 8),
              Expanded(
                child: assignedAsync.when(
                  loading: () => const Center(child: CircularProgressIndicator()),
                  error: (e, _) => Center(child: Text('배정 목록을 불러오지 못했습니다: $e')),
                  data: (assigned) {
                    if (assigned.isEmpty) {
                      return const Center(child: Text('배정된 직원이 없습니다.'));
                    }
                    return ListView.builder(
                      controller: scrollController,
                      itemCount: assigned.length,
                      itemBuilder: (context, i) {
                        final AssignableUser u = assigned[i];
                        return Card(
                          child: ListTile(
                            title: Text(u.name),
                            subtitle: Text(u.employeeNumber),
                            trailing: TextButton(
                              onPressed: _busy ? null : () => _remove(u.id),
                              style: TextButton.styleFrom(foregroundColor: Colors.red),
                              child: const Text('해제'),
                            ),
                          ),
                        );
                      },
                    );
                  },
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
